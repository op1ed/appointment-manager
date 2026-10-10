package com.op1ed.appointmentmanager.appointment.web.dto;

import java.time.OffsetDateTime;

import com.op1ed.appointmentmanager.appointment.application.CreateSlotCommand;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record CreateSlotRequest(
        @NotNull
        @Future
        OffsetDateTime startTime,

        @NotNull
        @Future
        OffsetDateTime endTime
) {
    public CreateSlotCommand toCommand() {
        return new CreateSlotCommand(startTime.toInstant(), endTime.toInstant());
    }
}
