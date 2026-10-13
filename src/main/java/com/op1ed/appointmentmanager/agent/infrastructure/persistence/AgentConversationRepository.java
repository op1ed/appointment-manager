package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.agent.domain.AgentConversation;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, String> {
    Optional<AgentConversation> findByIdAndOwnerId(String id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AgentConversation c where c.id = :id and c.ownerId = :ownerId")
    Optional<AgentConversation> findByIdAndOwnerIdForUpdate(@Param("id") String id, @Param("ownerId") Long ownerId);
}
