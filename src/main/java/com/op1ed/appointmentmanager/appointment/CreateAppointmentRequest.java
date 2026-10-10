package com.op1ed.appointmentmanager.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateAppointmentRequest(
        @NotBlank
        @Size(max = 100)
        String customerName,

        @NotNull
        @Positive
        Long slotId
) {
}