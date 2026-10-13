package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_conversations")
public class AgentConversation {
    @Id @Column(length = 36)
    private String id;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(nullable = false)
    private long revision;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
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
