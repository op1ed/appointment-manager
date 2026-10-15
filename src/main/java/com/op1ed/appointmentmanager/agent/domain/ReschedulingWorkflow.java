package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("agent_rescheduling_workflows")
public class ReschedulingWorkflow {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long ownerId;
    private Long appointmentId;
    private long revision;
    private String status;
    private String nextNode;
    private String stateJson;
    private Instant createdAt;
    private Instant updatedAt;

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
