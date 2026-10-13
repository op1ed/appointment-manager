package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import com.op1ed.appointmentmanager.agent.domain.WorkflowCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowCheckpointRepository extends JpaRepository<WorkflowCheckpoint, Long> {
    Optional<WorkflowCheckpoint> findByWorkflowIdAndCheckpointId(String workflowId, String checkpointId);
    Optional<WorkflowCheckpoint> findFirstByWorkflowIdOrderByIdDesc(String workflowId);
    List<WorkflowCheckpoint> findByWorkflowIdOrderByIdDesc(String workflowId);
}
