package com.op1ed.appointmentmanager.appointment;

import java.time.OffsetDateTime;

public record SlotResponse(
        Long id,
        Long doctorId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        boolean available
) {
}