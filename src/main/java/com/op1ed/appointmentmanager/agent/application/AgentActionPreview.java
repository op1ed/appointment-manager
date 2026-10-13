package com.op1ed.appointmentmanager.agent.application;

import java.time.Instant;

import com.op1ed.appointmentmanager.appointment.application.AppointmentResult;

public record AgentActionPreview(String summary, AppointmentResult before, ProposedAppointment after) {
    public record ProposedAppointment(String customerName, String doctorName, Long slotId,
            Instant startTime, Instant endTime) {
    }
}
