package com.op1ed.appointmentmanager.appointment;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SlotService {

    private final DoctorRepository doctorRepository;
    private final AppointmentSlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;

    public SlotService(
            DoctorRepository doctorRepository,
            AppointmentSlotRepository slotRepository,
            AppointmentRepository appointmentRepository
    ) {
        this.doctorRepository = doctorRepository;
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public SlotResponse create(
            long doctorId,
            CreateSlotRequest request
    ) {
        // 创建时段的第一条数据库查询：锁住对应医生。
        doctorRepository.findByIdForUpdate(doctorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "医生不存在"
                ));

        Instant startTime = request.startTime()
                .toInstant()
                .truncatedTo(ChronoUnit.MICROS);

        Instant endTime = request.endTime()
                .toInstant()
                .truncatedTo(ChronoUnit.MICROS);

        if (!startTime.isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "时段开始时间必须在未来"
            );
        }

        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "结束时间必须晚于开始时间"
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
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该医生已有重叠时段"
            );
        }

        AppointmentSlot slot = new AppointmentSlot(
                doctorId,
                startTime,
                endTime
        );

        return toResponse(slotRepository.save(slot), true);
    }

    public List<SlotResponse> findByDoctorId(long doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "医生不存在"
            );
        }

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
                .map(slot -> toResponse(
                        slot,
                        slot.getStartTime().isAfter(now)
                                && !occupiedSlotIds.contains(slot.getId())
                ))
                .toList();
    }

    private SlotResponse toResponse(
            AppointmentSlot slot,
            boolean available
    ) {
        return new SlotResponse(
                slot.getId(),
                slot.getDoctorId(),
                slot.getStartTime().atOffset(ZoneOffset.UTC),
                slot.getEndTime().atOffset(ZoneOffset.UTC),
                available
        );
    }
}