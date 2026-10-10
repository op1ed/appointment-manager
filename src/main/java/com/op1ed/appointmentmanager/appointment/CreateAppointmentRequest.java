package com.op1ed.appointmentmanager.appointment;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAppointmentRequest(
        @NotBlank
        @Size(max = 100)
        String customerName,

        @NotBlank
        @Size(max = 100)
        String doctorName,

        @NotNull
        @Future
        OffsetDateTime startTime
) {
}