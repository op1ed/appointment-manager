package com.op1ed.appointmentmanager.appointment.web;

import java.net.URI;
import java.time.OffsetDateTime;

import com.op1ed.appointmentmanager.appointment.application.AppointmentQuery;
import com.op1ed.appointmentmanager.appointment.application.AppointmentService;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentPageResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
            @AuthenticationPrincipal UserIdentity caller,
            @Valid @RequestBody CreateAppointmentRequest request
    ) {
        AppointmentResponse appointment =
                AppointmentResponse.from(appointmentService.create(caller, request.toCommand()));

        URI location = URI.create(
                "/api/appointments/" + appointment.id()
        );

        return ResponseEntity.created(location).body(appointment);
    }

    @GetMapping
    public AppointmentPageResponse listAppointments(
            @AuthenticationPrincipal UserIdentity caller,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "status", required = false) AppointmentStatus status,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        AppointmentQuery query = new AppointmentQuery(
                page,
                size,
                status,
                customerName,
                from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant()
        );
        return AppointmentPageResponse.from(appointmentService.search(caller, query));
    }

    @GetMapping("/{id}")
    public AppointmentResponse getAppointment(
            @AuthenticationPrincipal UserIdentity caller,
            @PathVariable("id") long id
    ) {
        return appointmentService.findById(caller, id)
                .map(AppointmentResponse::from)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "预约不存在"
                ));
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancelAppointment(
            @AuthenticationPrincipal UserIdentity caller,
            @PathVariable("id") long id
    ) {
        return appointmentService.cancel(caller, id)
                .map(AppointmentResponse::from)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "预约不存在"
                ));
    }
}
