package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("agent_conversations")
public class AgentConversation {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long ownerId;
    private long revision;
    private Instant createdAt;
    private Instant updatedAt;

    protected AgentConversation() {
    }

    public AgentConversation(String id, Long ownerId, Instant now) {
        this.id = id;
        this.ownerId = ownerId;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = createdAt;
    }

    public long nextTurn(Instant now) {
        revision = Math.addExact(revision, 1);
        updatedAt = now.truncatedTo(ChronoUnit.MICROS);
        return revision;
    }

    public String getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public long getRevision() { return revision; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
