package com.op1ed.appointmentmanager.agent.application;

import java.util.List;

public record AgentModelReply(String content, List<AgentModelToolCall> toolCalls) {
    public AgentModelReply {
        toolCalls = List.copyOf(toolCalls);
    }
}
