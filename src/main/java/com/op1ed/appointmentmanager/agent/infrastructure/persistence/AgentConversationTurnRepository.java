package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.agent.domain.AgentConversationTurn;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentConversationTurnRepository extends JpaRepository<AgentConversationTurn, Long> {
    Optional<AgentConversationTurn> findByConversationIdAndRequestId(String conversationId, String requestId);
    List<AgentConversationTurn> findTop10ByConversationIdOrderByTurnNumberDesc(String conversationId);
}
