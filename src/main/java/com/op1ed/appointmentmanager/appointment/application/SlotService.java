package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentRepository;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentSlotRepository;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SlotService {

    private final DoctorService doctorService;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;

    public SlotService(
            DoctorService doctorService,
            AppointmentSlotRepository slotRepository,
            AppointmentRepository appointmentRepository
    ) {
        this.doctorService = doctorService;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public SlotResult create(
            long doctorId,
            CreateSlotCommand command
    ) {
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
                slotRepository
                        .existsByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThan(
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

        return toResult(slotRepository.save(slot), true);
    }

    public List<SlotResult> findByDoctorId(long doctorId) {
        doctorService.requireById(doctorId);

        List<AppointmentSlot> slots =
                slotRepository.findByDoctorIdOrderByStartTimeAsc(doctorId);

        if (slots.isEmpty()) {
            return List.of();
        }

        List<Long> slotIds = slots.stream()
                .map(AppointmentSlot::getId)
                .toList();

        Set<Long> occupiedSlotIds = appointmentRepository
                .findBySlotIdInAndStatus(slotIds, AppointmentStatus.BOOKED)
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
