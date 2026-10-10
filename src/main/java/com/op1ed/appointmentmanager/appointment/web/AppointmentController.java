package com.op1ed.appointmentmanager.appointment.web;

import java.net.URI;
import java.util.List;

import com.op1ed.appointmentmanager.appointment.application.AppointmentService;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
                AppointmentResponse.from(appointmentService.create(request.toCommand()));

        URI location = URI.create(
                "/api/appointments/" + appointment.id()
        );

        return ResponseEntity.created(location).body(appointment);
    }

    @GetMapping
    public List<AppointmentResponse> listAppointments() {
        return appointmentService.findAll().stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AppointmentResponse getAppointment(
            @PathVariable("id") long id
    ) {
        return appointmentService.findById(id)
                .map(AppointmentResponse::from)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "预约不存在"
                ));
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancelAppointment(
            @PathVariable("id") long id
    ) {
        return appointmentService.cancel(id)
                .map(AppointmentResponse::from)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "预约不存在"
                ));
    }
}
