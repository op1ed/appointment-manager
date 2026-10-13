package com.op1ed.appointmentmanager.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity @Table(name = "agent_workflow_checkpoints")
public class WorkflowCheckpoint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "checkpoint_id", nullable = false, length = 36) private String checkpointId;
    @Column(name = "workflow_id", nullable = false, length = 36) private String workflowId;
    @Column(name = "node_id", nullable = false, length = 80) private String nodeId;
    @Column(name = "next_node_id", nullable = false, length = 80) private String nextNodeId;
    @Column(name = "state_json", nullable = false, columnDefinition = "MEDIUMTEXT") private String stateJson;

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
