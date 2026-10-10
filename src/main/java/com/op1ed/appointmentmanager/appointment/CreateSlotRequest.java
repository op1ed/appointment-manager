package com.op1ed.appointmentmanager.appointment;

import java.time.OffsetDateTime;

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
}