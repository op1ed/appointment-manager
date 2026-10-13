package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentAction;

public record AppointmentHistoryEventResult(
        Long id, Long actorId, AppointmentAction action,
        Long fromSlotId, Long toSlotId, Instant occurredAt
) {
}
