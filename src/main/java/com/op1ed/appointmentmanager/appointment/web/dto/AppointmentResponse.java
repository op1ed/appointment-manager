package com.op1ed.appointmentmanager.appointment.web.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.op1ed.appointmentmanager.appointment.application.AppointmentResult;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;

public record AppointmentResponse(
        Long id,
        Long slotId,
        String customerName,
        String doctorName,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        AppointmentStatus status
) {
    public static AppointmentResponse from(AppointmentResult result) {
        return new AppointmentResponse(
                result.id(),
                result.slotId(),
                result.customerName(),
                result.doctorName(),
                result.startTime().atOffset(ZoneOffset.UTC),
                result.endTime() == null
                        ? null
                        : result.endTime().atOffset(ZoneOffset.UTC),
                result.status()
        );
    }
}
