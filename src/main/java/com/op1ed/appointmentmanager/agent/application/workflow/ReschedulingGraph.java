package com.op1ed.appointmentmanager.agent.application.workflow;

import com.op1ed.appointmentmanager.identity.application.UserIdentity;

/** 工作流执行端口。框架的 Graph、Checkpoint 和 RunnableConfig 留在适配层。 */
public interface ReschedulingGraph {
    GraphResult start(UserIdentity caller, String workflowId, WorkflowCommands.Start command);
    GraphResult resume(UserIdentity caller, String workflowId, String nextNode, WorkflowCommands.Signal signal,
            Long targetSlotId, String note);

    record GraphResult(String stateJson, String status, String nextNode, String actionId) { }
}
