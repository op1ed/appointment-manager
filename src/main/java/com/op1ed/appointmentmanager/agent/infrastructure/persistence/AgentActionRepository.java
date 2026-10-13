package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.agent.domain.AgentAction;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentActionRepository extends JpaRepository<AgentAction, String> {
    Optional<AgentAction> findByIdAndOwnerId(String id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AgentAction a where a.id = :id and a.ownerId = :ownerId")
    Optional<AgentAction> findByIdAndOwnerIdForUpdate(@Param("id") String id, @Param("ownerId") Long ownerId);
}
