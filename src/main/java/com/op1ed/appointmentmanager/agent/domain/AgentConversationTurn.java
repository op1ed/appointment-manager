package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("agent_conversation_turns")
public class AgentConversationTurn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String requestId;
    private long turnNumber;
    private String userMessage;
    private String messagesJson;
    private String responseJson;
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
