package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentRepository;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentSlotRepository;
import com.op1ed.appointmentmanager.doctor.application.DoctorInfo;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentSlotRepository slotRepository;
    private final DoctorService doctorService;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            AppointmentSlotRepository slotRepository,
            DoctorService doctorService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.doctorService = doctorService;
    }

    @Transactional
    public AppointmentResult create(BookAppointmentCommand command) {
        // 创建预约的第一条数据库查询：锁住对应时段。
        AppointmentSlot slot = slotRepository
                .findByIdForUpdate(command.slotId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "预约时段不存在"
                ));

        if (!slot.isInFutureAt(Instant.now())) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "该时段已经开始，不能预约"
            );
        }

        DoctorInfo doctor = doctorService.requireById(slot.getDoctorId());

        boolean occupied = appointmentRepository.existsBySlotIdAndStatus(
                slot.getId(),
                AppointmentStatus.BOOKED
        );

        if (occupied) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "该时段已经被预约"
            );
        }

        Appointment appointment = new Appointment(
                command.customerName(),
                slot.getId(),
                doctor.name(),
                slot.getStartTime(),
                slot.getEndTime()
        );

        Appointment saved = appointmentRepository.saveAndFlush(appointment);
        return toResult(saved);
    }

    public List<AppointmentResult> findAll() {
        return appointmentRepository
                .findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResult)
                .toList();
    }

    public Optional<AppointmentResult> findById(long id) {
        return appointmentRepository.findById(id)
                .map(this::toResult);
    }

    @Transactional
    public Optional<AppointmentResult> cancel(long id) {
        return appointmentRepository.findById(id)
                .map(appointment -> {
                    if (appointment.getSlotId() != null) {
                        slotRepository
                                .findByIdForUpdate(appointment.getSlotId())
                                .orElseThrow(() -> new BusinessException(
                                        ErrorCode.NOT_FOUND,
                                        "预约时段不存在"
                                ));
                    }

                    appointment.cancel();
                    return toResult(appointment);
                });
    }

    private AppointmentResult toResult(Appointment appointment) {
        return new AppointmentResult(
                appointment.getId(),
                appointment.getSlotId(),
                appointment.getCustomerName(),
                appointment.getDoctorName(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus()
        );
    }
}
