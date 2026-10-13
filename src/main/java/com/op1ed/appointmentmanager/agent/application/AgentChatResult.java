package com.op1ed.appointmentmanager.agent.application;

import java.util.List;

public record AgentChatResult(String conversationId, String requestId, String status, String reply,
        List<AgentActionResult> actions, List<AgentToolExecution> toolExecutions, int modelCalls, String failureCode) {
    public AgentChatResult {
        actions = List.copyOf(actions);
        toolExecutions = List.copyOf(toolExecutions);
    }
}
