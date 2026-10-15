package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("agent_workflow_requests")
public class WorkflowRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String requestId;
    private String workflowId;
    private String kind;
    private String payloadJson;
    private String responseJson;
    private Instant createdAt;

    protected WorkflowRequest() { }
    public WorkflowRequest(Long ownerId, String requestId, String workflowId, String kind, String payloadJson, String responseJson) {
        this.ownerId = ownerId; this.requestId = requestId; this.workflowId = workflowId; this.kind = kind;
        this.payloadJson = payloadJson; this.responseJson = responseJson;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    public String getWorkflowId() { return workflowId; }
    public String getKind() { return kind; }
    public String getPayloadJson() { return payloadJson; }
    public String getResponseJson() { return responseJson; }
}
