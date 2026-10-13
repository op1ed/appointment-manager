package com.op1ed.appointmentmanager.agent.application;

import java.time.Instant;
import java.util.List;

public record AgentConversationView(String id, Instant createdAt, Instant updatedAt, long revision, List<TurnView> turns) {
    public AgentConversationView {
        turns = List.copyOf(turns);
    }

    public record TurnView(String requestId, String message, Instant createdAt, AgentChatResult response) {
    }
}
