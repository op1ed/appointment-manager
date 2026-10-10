package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;

public record SlotResult(
        Long id,
        Long doctorId,
        Instant startTime,
        Instant endTime,
        boolean available
) {
}
