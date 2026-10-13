package com.op1ed.appointmentmanager.agent.infrastructure.springai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.op1ed.appointmentmanager.agent.application.AgentMessage;
import com.op1ed.appointmentmanager.agent.application.AgentModelReply;
import com.op1ed.appointmentmanager.agent.application.AgentModelToolCall;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

final class SpringAiMessages {
    private SpringAiMessages() { }

    static List<Message> toSpring(List<AgentMessage> messages) {
        List<Message> result = new ArrayList<>();
        var names = new HashMap<String, String>();
        for (AgentMessage message : messages) {
            switch (message.role()) {
                case "system" -> result.add(new SystemMessage(message.content()));
                case "user" -> result.add(new UserMessage(message.content()));
                case "assistant" -> {
                    message.toolCalls().forEach(call -> names.put(call.id(), call.name()));
                    result.add(assistant(new AgentModelReply(message.content(), message.toolCalls())));
                }
                case "tool" -> result.add(ToolResponseMessage.builder().responses(List.of(
                        new ToolResponseMessage.ToolResponse(message.toolCallId(), names.get(message.toolCallId()), message.content()))).build());
                default -> throw new IllegalArgumentException("未知消息角色");
            }
        }
        return result;
    }

    static AssistantMessage assistant(AgentModelReply reply) {
        return AssistantMessage.builder().content(reply.content() == null ? "" : reply.content())
                .toolCalls(reply.toolCalls().stream().map(call -> new AssistantMessage.ToolCall(
                        call.id(), "function", call.name(), call.arguments())).toList()).build();
    }

    static List<AgentMessage> fromSpring(List<Message> messages) {
        List<AgentMessage> result = new ArrayList<>();
        for (Message message : messages) {
            if (message instanceof AssistantMessage assistant) {
                result.add(new AgentMessage("assistant", assistant.getText(), assistant.getToolCalls().stream()
                        .map(call -> new AgentModelToolCall(call.id(), call.name(), call.arguments())).toList(), null));
            } else if (message instanceof ToolResponseMessage tool) {
                tool.getResponses().forEach(response -> result.add(AgentMessage.tool(response.id(), response.responseData())));
            } else {
                result.add(AgentMessage.text(message.getMessageType().getValue(), message.getText()));
            }
        }
        return result;
    }
}
