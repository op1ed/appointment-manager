package com.op1ed.appointmentmanager.appointment;

import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository
    ) {
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request) {
        Appointment appointment = new Appointment(
                request.customerName().strip(),
                request.doctorName().strip(),
                request.startTime()
                        .toInstant()
                        .truncatedTo(ChronoUnit.MICROS)
        );

        Appointment saved = appointmentRepository.save(appointment);

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
                    appointment.cancel();
                    return toResponse(appointment);
                });
    }

    private AppointmentResponse toResponse(Appointment appointment) {
    return new AppointmentResponse(
            appointment.getId(),
            appointment.getCustomerName(),
            appointment.getDoctorName(),
            appointment.getStartTime().atOffset(ZoneOffset.UTC),
            appointment.getStatus()
    );
}
}