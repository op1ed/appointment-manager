package com.op1ed.appointmentmanager.agent.application;

import java.util.List;

public record AgentConversationSnapshot(String id, long revision, List<List<AgentMessage>> recentTurns) {
    public AgentConversationSnapshot {
        recentTurns = recentTurns.stream().map(List::copyOf).toList();
    }
}
