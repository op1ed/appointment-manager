package com.op1ed.appointmentmanager.agent.application;

import java.util.List;

public record AgentMessage(String role, String content, List<AgentModelToolCall> toolCalls, String toolCallId) {
    public AgentMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static AgentMessage text(String role, String content) {
        return new AgentMessage(role, content, List.of(), null);
    }

    public static AgentMessage assistant(AgentModelReply reply) {
        return new AgentMessage("assistant", reply.content(), reply.toolCalls(), null);
    }

    public static AgentMessage tool(String callId, String content) {
        return new AgentMessage("tool", content, List.of(), callId);
    }
}
