package com.op1ed.appointmentmanager.agent.application;

import java.util.List;
import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingWorkflowView;

public record AgentRunResult(String status, String reply, List<AgentActionResult> actions,
        List<AgentToolExecution> toolExecutions, int modelCalls, String failureCode,
        List<AgentMessage> generatedMessages, List<ReschedulingWorkflowView> workflows) {
    public AgentRunResult {
        actions = List.copyOf(actions);
        toolExecutions = List.copyOf(toolExecutions);
        generatedMessages = List.copyOf(generatedMessages);
        workflows = List.copyOf(workflows);
    }
}
