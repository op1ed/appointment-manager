package com.op1ed.appointmentmanager.agent.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.agent.domain.AgentConversation;
import com.op1ed.appointmentmanager.agent.domain.AgentConversationTurn;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.AgentConversationMapper;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.AgentConversationTurnMapper;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.op1ed.appointmentmanager.shared.persistence.PersistenceChecks.requireUpdatedRow;

@Service
@Transactional(readOnly = true)
public class AgentConversationService {
    private final AgentConversationMapper conversations;
    private final AgentConversationTurnMapper turns;
    private final ObjectMapper json;

    public AgentConversationService(AgentConversationMapper conversations, AgentConversationTurnMapper turns, ObjectMapper json) {
        this.conversations = conversations;
        this.turns = turns;
        this.json = json;
    }

    @Transactional
    public AgentConversationView create(UserIdentity caller) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        AgentConversation conversation = new AgentConversation(UUID.randomUUID().toString(), user.id(), Instant.now());
        conversations.insert(conversation);
        return new AgentConversationView(conversation.getId(), conversation.getCreatedAt(), conversation.getUpdatedAt(), 0, List.of());
    }

    public AgentConversationView find(UserIdentity caller, String id) {
        AgentConversation conversation = requireConversation(caller, id);
        var recent = turns.recent(conversation.getId()).reversed().stream()
                .map(turn -> new AgentConversationView.TurnView(turn.getRequestId(), turn.getUserMessage(), turn.getCreatedAt(), result(turn))).toList();
        return new AgentConversationView(conversation.getId(), conversation.getCreatedAt(), conversation.getUpdatedAt(),
                conversation.getRevision(), recent);
    }

    public Optional<AgentChatResult> previous(UserIdentity caller, String id, AgentChatCommand command) {
        AgentConversation conversation = requireConversation(caller, id);
        return turns.findRequest(conversation.getId(), command.requestId()).map(turn -> {
            requireSameMessage(turn, command);
            return result(turn);
        });
    }

    public AgentConversationSnapshot snapshot(UserIdentity caller, String id) {
        AgentConversation conversation = requireConversation(caller, id);
        var recent = turns.recent(conversation.getId()).reversed().stream()
                .map(this::messages).toList();
        return new AgentConversationSnapshot(conversation.getId(), conversation.getRevision(), recent);
    }

    @Transactional
    public AgentChatResult save(UserIdentity caller, AgentConversationSnapshot snapshot, AgentChatCommand command,
            List<AgentMessage> messages, AgentChatResult response) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        // 模型已调用完毕。短事务先锁会话，避免覆盖另一请求已经保存的上下文。
        AgentConversation conversation = conversations.lockOwned(snapshot.id(), user.id())
                .orElseThrow(this::notFound);
        var existing = turns.findRequest(snapshot.id(), command.requestId());
        if (existing.isPresent()) {
            requireSameMessage(existing.get(), command);
            return result(existing.get());
        }
        if (conversation.getRevision() != snapshot.revision()) {
            throw new BusinessException(ErrorCode.CONFLICT, "会话已收到其他消息，请刷新后重试");
        }
        Instant now = Instant.now();
        turns.insert(new AgentConversationTurn(snapshot.id(), command.requestId(), conversation.nextTurn(now),
                command.message(), write(messages), write(response), now));
        requireUpdatedRow(conversations.updateById(conversation));
        return response;
    }

    private AgentConversation requireConversation(UserIdentity caller, String id) {
        UserIdentity user = UserIdentity.requireAuthenticated(caller);
        return conversations.findOwned(AgentIds.requireUuid(id, "会话 ID"), user.id()).orElseThrow(this::notFound);
    }

    private void requireSameMessage(AgentConversationTurn turn, AgentChatCommand command) {
        if (!turn.getUserMessage().equals(command.message())) {
            throw new BusinessException(ErrorCode.CONFLICT, "同一请求 ID 不能用于不同消息");
        }
    }

    private AgentChatResult result(AgentConversationTurn turn) {
        try {
            return json.readValue(turn.getResponseJson(), AgentChatResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取保存的对话结果", exception);
        }
    }

    private List<AgentMessage> messages(AgentConversationTurn turn) {
        try {
            return json.readValue(turn.getMessagesJson(), new TypeReference<List<AgentMessage>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法读取保存的对话上下文", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法保存对话", exception);
        }
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "会话不存在");
    }
}
