package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;

public record AppointmentResult(
        Long id,
        Long slotId,
        String customerName,
        String doctorName,
        Instant startTime,
        Instant endTime,
        AppointmentStatus status
) {
}
