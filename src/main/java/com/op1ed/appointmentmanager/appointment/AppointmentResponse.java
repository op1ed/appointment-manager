package com.op1ed.appointmentmanager.appointment;

import java.time.LocalDateTime;

public record AppointmentResponse(
        Long id,
        String customerName,
        String doctorName,
        LocalDateTime startTime
) {
}