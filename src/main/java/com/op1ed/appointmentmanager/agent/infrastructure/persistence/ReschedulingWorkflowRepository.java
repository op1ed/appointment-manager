package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.Optional;
import com.op1ed.appointmentmanager.agent.domain.ReschedulingWorkflow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReschedulingWorkflowRepository extends JpaRepository<ReschedulingWorkflow, String> {
    Optional<ReschedulingWorkflow> findByIdAndOwnerId(String id, Long ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ReschedulingWorkflow w where w.id = :id and w.ownerId = :ownerId")
    Optional<ReschedulingWorkflow> lockOwned(@Param("id") String id, @Param("ownerId") Long ownerId);
}
