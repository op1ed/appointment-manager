package com.op1ed.appointmentmanager.appointment;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final DoctorRepository doctorRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            DoctorRepository doctorRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        // 创建预约的第一条数据库查询：锁住对应时段。
        AppointmentSlot slot = slotRepository
                .findByIdForUpdate(request.slotId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "预约时段不存在"
                ));

        if (!slot.getStartTime().isAfter(Instant.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "该时段已经开始，不能预约"
            );
        }

        Doctor doctor = doctorRepository.findById(slot.getDoctorId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "医生不存在"
                ));

        boolean occupied = appointmentRepository.existsBySlotIdAndStatus(
                slot.getId(),
                AppointmentStatus.BOOKED
        );

        if (occupied) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该时段已经被预约"
            );
        }

        Appointment appointment = new Appointment(
                request.customerName().strip(),
                slot.getId(),
                doctor.getName(),
                slot.getStartTime(),
                slot.getEndTime()
        );

        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        return toResponse(saved);
    }

    public List<AppointmentResponse> findAll() {
        return appointmentRepository
                .findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<AppointmentResponse> findById(long id) {
        return appointmentRepository.findById(id)
                .map(this::toResponse);
    }

    @Transactional
    public Optional<AppointmentResponse> cancel(long id) {
        return appointmentRepository.findById(id)
                .map(appointment -> {
                    if (appointment.getSlotId() != null) {
                        slotRepository
                                .findByIdForUpdate(appointment.getSlotId())
                                .orElseThrow(() -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "预约时段不存在"
                                ));
                    }

                    appointment.cancel();
                    return toResponse(appointment);
                });
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getSlotId(),
                appointment.getCustomerName(),
                appointment.getDoctorName(),
                appointment.getStartTime().atOffset(ZoneOffset.UTC),
                appointment.getEndTime() == null
                        ? null
                        : appointment.getEndTime().atOffset(ZoneOffset.UTC),
                appointment.getStatus()
        );
    }
}