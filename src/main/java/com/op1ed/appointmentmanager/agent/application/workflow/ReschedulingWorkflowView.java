package com.op1ed.appointmentmanager.agent.application.workflow;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.op1ed.appointmentmanager.agent.application.AgentActionResult;
import com.op1ed.appointmentmanager.appointment.application.SlotResult;

public record ReschedulingWorkflowView(String id, long revision, String status, String nextNode,
        Long appointmentId, Long expectedSlotId, Long doctorId, String doctorName, Instant from, Instant to,
        JsonNode appointment, List<SlotResult> candidates, Long selectedSlotId, AgentActionResult action,
        String note, Instant createdAt, Instant updatedAt) {
    public ReschedulingWorkflowView { candidates = List.copyOf(candidates); }
}
