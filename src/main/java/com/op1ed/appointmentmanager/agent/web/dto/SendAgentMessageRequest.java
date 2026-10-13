package com.op1ed.appointmentmanager.agent.web.dto;

import com.op1ed.appointmentmanager.agent.application.AgentChatCommand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendAgentMessageRequest(@NotBlank @Size(max = 36) String requestId,
        @NotBlank @Size(max = 2000) String message) {
    public AgentChatCommand toCommand() {
        return new AgentChatCommand(requestId, message);
    }
}
