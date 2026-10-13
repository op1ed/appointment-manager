package com.op1ed.appointmentmanager.appointment.web.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.op1ed.appointmentmanager.appointment.application.AppointmentHistoryEventResult;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentAction;

public record AppointmentHistoryEventResponse(
        Long id, Long actorId, AppointmentAction action,
        Long fromSlotId, Long toSlotId, OffsetDateTime occurredAt
) {
    public static AppointmentHistoryEventResponse from(AppointmentHistoryEventResult result) {
        return new AppointmentHistoryEventResponse(result.id(), result.actorId(), result.action(),
                result.fromSlotId(), result.toSlotId(), result.occurredAt().atOffset(ZoneOffset.UTC));
    }
}
