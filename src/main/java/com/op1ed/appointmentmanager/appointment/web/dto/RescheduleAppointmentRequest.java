package com.op1ed.appointmentmanager.appointment.web.dto;

import com.op1ed.appointmentmanager.appointment.application.RescheduleAppointmentCommand;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RescheduleAppointmentRequest(
        @NotNull @Positive Long expectedSlotId,
        @NotNull @Positive Long targetSlotId
) {
    public RescheduleAppointmentCommand toCommand() {
        return new RescheduleAppointmentCommand(expectedSlotId, targetSlotId);
    }
}
