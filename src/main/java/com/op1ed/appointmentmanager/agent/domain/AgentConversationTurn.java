package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_conversation_turns")
public class AgentConversationTurn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;
    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;
    @Column(name = "turn_number", nullable = false)
    private long turnNumber;
    @Column(name = "user_message", nullable = false, length = 2000)
    private String userMessage;
    @Column(name = "messages_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String messagesJson;
    @Column(name = "response_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String responseJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentConversationTurn() {
    }

    public AgentConversationTurn(String conversationId, String requestId, long turnNumber, String userMessage,
            String messagesJson, String responseJson, Instant now) {
        this.conversationId = conversationId;
        this.requestId = requestId;
        this.turnNumber = turnNumber;
        this.userMessage = userMessage;
        this.messagesJson = messagesJson;
        this.responseJson = responseJson;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public String getRequestId() { return requestId; }
    public String getUserMessage() { return userMessage; }
    public String getMessagesJson() { return messagesJson; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
}
