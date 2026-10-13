package com.op1ed.appointmentmanager.agent.application;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentActionResult(String id, String kind, String status, Instant createdAt,
        Instant expiresAt, Instant confirmedAt, JsonNode preview, JsonNode result, String workflowId) {
    public AgentActionResult(String id, String kind, String status, Instant createdAt,
            Instant expiresAt, Instant confirmedAt, JsonNode preview, JsonNode result) {
        this(id, kind, status, createdAt, expiresAt, confirmedAt, preview, result, null);
    }
}
