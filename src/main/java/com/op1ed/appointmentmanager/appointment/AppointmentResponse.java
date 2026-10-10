package com.op1ed.appointmentmanager.appointment;

import java.time.OffsetDateTime;

public record AppointmentResponse(
        Long id,
        String customerName,
        String doctorName,
        OffsetDateTime startTime
) {
}