package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentMapper;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentSlotMapper;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;
import com.op1ed.appointmentmanager.shared.persistence.StoragePage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SlotService {

    private final DoctorService doctorService;
    private final AppointmentSlotMapper slotMapper;
    private final AppointmentMapper appointmentMapper;

    public SlotService(
            DoctorService doctorService,
            AppointmentSlotMapper slotMapper,
            AppointmentMapper appointmentMapper
    ) {
        this.doctorService = doctorService;
        this.slotMapper = slotMapper;
        this.appointmentMapper = appointmentMapper;
    }

    @Transactional
    public SlotResult create(
            UserIdentity caller,
            long doctorId,
            CreateSlotCommand command
    ) {
        UserIdentity.requireAuthenticated(caller).requireAdministrator();
        // 创建时段的第一条数据库查询：锁住对应医生。
        doctorService.lockForScheduling(doctorId);

        AppointmentSlot slot = new AppointmentSlot(
                doctorId, command.startTime(), command.endTime()
        );
        Instant startTime = slot.getStartTime();
        Instant endTime = slot.getEndTime();

        if (!slot.isInFutureAt(Instant.now())) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "时段开始时间必须在未来"
            );
        }

        boolean overlaps =
                slotMapper
                        .overlaps(
                                doctorId,
                                endTime,
                                startTime
                        );

        if (overlaps) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "该医生已有重叠时段"
            );
        }

        slotMapper.insert(slot);
        return toResult(slot, true);
    }

    public List<SlotResult> findByDoctorId(long doctorId) {
        doctorService.requireById(doctorId);

        List<AppointmentSlot> slots =
                slotMapper.listByDoctor(doctorId);

        if (slots.isEmpty()) {
            return List.of();
        }

        List<Long> slotIds = slots.stream()
                .map(AppointmentSlot::getId)
                .toList();

        Set<Long> occupiedSlotIds = appointmentMapper
                .findBySlots(slotIds, AppointmentStatus.BOOKED)
                .stream()
                .map(Appointment::getSlotId)
                .collect(Collectors.toSet());

        Instant now = Instant.now();

        return slots.stream()
                .map(slot -> toResult(
                        slot,
                        slot.isInFutureAt(now)
                                && !occupiedSlotIds.contains(slot.getId())
                ))
                .toList();
    }

    public Optional<SlotResult> findById(long id) {
        BusinessInputs.requirePositiveId(id, "时段 ID");
        return Optional.ofNullable(slotMapper.selectById(id)).map(slot -> toResult(slot,
                slot.isInFutureAt(Instant.now()) && !appointmentMapper.existsOccupied(id, AppointmentStatus.BOOKED)));
    }

    public SlotPageResult searchAvailable(long doctorId, AvailableSlotQuery query) {
        doctorService.requireById(doctorId);
        var page = StoragePage.from(slotMapper.searchAvailable(StoragePage.request(query.page(), query.size()),
                doctorId, Instant.now(), query.fromTime(), query.toTime()))
                .map(slot -> toResult(slot, true));
        return new SlotPageResult(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), (long) page.getNumber() + 1 < page.getTotalPages());
    }

    private SlotResult toResult(
            AppointmentSlot slot,
            boolean available
    ) {
        return new SlotResult(
                slot.getId(),
                slot.getDoctorId(),
                slot.getStartTime(),
                slot.getEndTime(),
                available
        );
    }
}
