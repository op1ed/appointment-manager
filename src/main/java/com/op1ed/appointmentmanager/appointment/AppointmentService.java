package com.op1ed.appointmentmanager.appointment;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service
public class AppointmentService {

    private final Map<Long, AppointmentResponse> appointments =
            new ConcurrentHashMap<>();

    private final AtomicLong nextId = new AtomicLong();

    public AppointmentResponse create(CreateAppointmentRequest request) {
        long id = nextId.incrementAndGet();

        AppointmentResponse appointment = new AppointmentResponse(
                id,
                request.customerName().strip(),
                request.doctorName().strip(),
                request.startTime()
        );

        appointments.put(id, appointment);
        return appointment;
    }

    public List<AppointmentResponse> findAll() {
        return appointments.values().stream()
                .sorted(Comparator.comparing(AppointmentResponse::id))
                .toList();
    }

    public Optional<AppointmentResponse> findById(long id) {
        return Optional.ofNullable(appointments.get(id));
    }
}