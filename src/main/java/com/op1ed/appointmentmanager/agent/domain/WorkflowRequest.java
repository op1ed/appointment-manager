package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity @Table(name = "agent_workflow_requests")
public class WorkflowRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(name = "request_id", nullable = false, length = 36) private String requestId;
    @Column(name = "workflow_id", nullable = false, length = 36) private String workflowId;
    @Column(nullable = false, length = 16) private String kind;
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(name = "response_json", nullable = false, columnDefinition = "MEDIUMTEXT") private String responseJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

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
