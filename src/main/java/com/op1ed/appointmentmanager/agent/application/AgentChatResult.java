package com.op1ed.appointmentmanager.agent.application;

import java.util.List;
import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingWorkflowView;

public record AgentChatResult(String conversationId, String requestId, String status, String reply,
        List<AgentActionResult> actions, List<AgentToolExecution> toolExecutions, int modelCalls, String failureCode,
        List<ReschedulingWorkflowView> workflows) {
    public AgentChatResult {
        actions = List.copyOf(actions);
        toolExecutions = List.copyOf(toolExecutions);
        workflows = workflows == null ? List.of() : List.copyOf(workflows);
    }
    public AgentChatResult(String conversationId, String requestId, String status, String reply,
            List<AgentActionResult> actions, List<AgentToolExecution> toolExecutions, int modelCalls, String failureCode) {
        this(conversationId, requestId, status, reply, actions, toolExecutions, modelCalls, failureCode, List.of());
    }
}
