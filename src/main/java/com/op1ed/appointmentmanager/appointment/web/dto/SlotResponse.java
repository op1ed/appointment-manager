package com.op1ed.appointmentmanager.appointment.web.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.op1ed.appointmentmanager.appointment.application.SlotResult;

public record SlotResponse(
        Long id,
        Long doctorId,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        boolean available
) {
    public static SlotResponse from(SlotResult result) {
        return new SlotResponse(
                result.id(),
                result.doctorId(),
                result.startTime().atOffset(ZoneOffset.UTC),
                result.endTime().atOffset(ZoneOffset.UTC),
                result.available()
        );
    }
}
