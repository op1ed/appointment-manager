package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_rescheduling_workflows")
public class ReschedulingWorkflow {
    @Id @Column(length = 36) private String id;
    @Column(name = "owner_id", nullable = false) private Long ownerId;
    @Column(name = "appointment_id", nullable = false) private Long appointmentId;
    @Column(nullable = false) private long revision;
    @Column(nullable = false, length = 32) private String status;
    @Column(name = "next_node", nullable = false, length = 80) private String nextNode;
    @Column(name = "state_json", nullable = false, columnDefinition = "MEDIUMTEXT") private String stateJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected ReschedulingWorkflow() { }
    public ReschedulingWorkflow(String id, Long ownerId, Long appointmentId, Instant now) {
        this.id = id; this.ownerId = ownerId; this.appointmentId = appointmentId;
        this.status = "CREATED"; this.nextNode = "START"; this.stateJson = "{}";
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS); this.updatedAt = createdAt;
    }
    public void advance(String stateJson, String status, String nextNode, Instant now) {
        this.stateJson = stateJson; this.status = status; this.nextNode = nextNode;
        this.revision = Math.addExact(revision, 1); this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }
    public boolean finished() { return status.equals("COMPLETED") || status.equals("REJECTED") || status.equals("ABORTED"); }
    public String getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public Long getAppointmentId() { return appointmentId; }
    public long getRevision() { return revision; }
    public String getStatus() { return status; }
    public String getNextNode() { return nextNode; }
    public String getStateJson() { return stateJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
