package com.op1ed.appointmentmanager.appointment;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request
    ) {
        AppointmentResponse appointment =
                appointmentService.create(request);

        URI location = URI.create(
                "/api/appointments/" + appointment.id()
        );

        return ResponseEntity.created(location).body(appointment);
    }

    @GetMapping
    public List<AppointmentResponse> listAppointments() {
        return appointmentService.findAll();
    }

    @GetMapping("/{id}")
    public AppointmentResponse getAppointment(
            @PathVariable("id") long id
    ) {
        return appointmentService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "预约不存在"
                ));
    }
}