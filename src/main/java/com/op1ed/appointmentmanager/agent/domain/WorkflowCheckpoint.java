package com.op1ed.appointmentmanager.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("agent_workflow_checkpoints")
public class WorkflowCheckpoint {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String checkpointId;
    private String workflowId;
    private String nodeId;
    private String nextNodeId;
    private String stateJson;

    protected WorkflowCheckpoint() { }
    public WorkflowCheckpoint(String checkpointId, String workflowId, String nodeId, String nextNodeId, String stateJson) {
        this.checkpointId = checkpointId; this.workflowId = workflowId; update(nodeId, nextNodeId, stateJson);
    }
    public void update(String nodeId, String nextNodeId, String stateJson) {
        this.nodeId = nodeId; this.nextNodeId = nextNodeId; this.stateJson = stateJson;
    }
    public String getCheckpointId() { return checkpointId; }
    public String getNodeId() { return nodeId; }
    public String getNextNodeId() { return nextNodeId; }
    public String getStateJson() { return stateJson; }
}
