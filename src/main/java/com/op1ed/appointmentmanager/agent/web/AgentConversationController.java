package com.op1ed.appointmentmanager.agent.web;

import java.net.URI;

import com.op1ed.appointmentmanager.agent.application.AgentChatResult;
import com.op1ed.appointmentmanager.agent.application.AgentChatService;
import com.op1ed.appointmentmanager.agent.application.AgentConversationService;
import com.op1ed.appointmentmanager.agent.application.AgentConversationView;
import com.op1ed.appointmentmanager.agent.web.dto.SendAgentMessageRequest;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/conversations")
public class AgentConversationController {
    private final AgentConversationService conversations;
    private final AgentChatService chat;

    public AgentConversationController(AgentConversationService conversations, AgentChatService chat) {
        this.conversations = conversations;
        this.chat = chat;
    }

    @PostMapping
    public ResponseEntity<AgentConversationView> create(@AuthenticationPrincipal UserIdentity caller) {
        AgentConversationView conversation = conversations.create(caller);
        return ResponseEntity.created(URI.create("/api/agent/conversations/" + conversation.id())).body(conversation);
    }

    @GetMapping("/{id}")
    public AgentConversationView find(@AuthenticationPrincipal UserIdentity caller, @PathVariable("id") String id) {
        return conversations.find(caller, id);
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<AgentChatResult> send(@AuthenticationPrincipal UserIdentity caller, @PathVariable("id") String id,
            @Valid @RequestBody SendAgentMessageRequest request) {
        AgentChatResult response = chat.send(caller, id, request.toCommand());
        int status = switch (response.failureCode() == null ? "" : response.failureCode()) {
            case "MODEL_ERROR" -> 502;
            case "MODEL_TIMEOUT" -> 504;
            default -> 200;
        };
        return ResponseEntity.status(status).body(response);
    }
}
