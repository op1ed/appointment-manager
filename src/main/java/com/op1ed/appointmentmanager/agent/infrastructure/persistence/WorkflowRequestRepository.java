package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.Optional;
import com.op1ed.appointmentmanager.agent.domain.WorkflowRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRequestRepository extends JpaRepository<WorkflowRequest, Long> {
    Optional<WorkflowRequest> findByOwnerIdAndRequestId(Long ownerId, String requestId);
}
