package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.agent.domain.WorkflowCheckpoint;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.WorkflowCheckpointMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class WorkflowCheckpointRepository extends MybatisRepository<WorkflowCheckpoint> {

    private final WorkflowCheckpointMapper mapper;

    public WorkflowCheckpointRepository(WorkflowCheckpointMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<WorkflowCheckpoint> findByWorkflowIdAndCheckpointId(String workflowId, String checkpointId) {
        return mapper.findCheckpoint(workflowId, checkpointId);
    }

    public Optional<WorkflowCheckpoint> findFirstByWorkflowIdOrderByIdDesc(String workflowId) {
        return mapper.latest(workflowId);
    }

    public List<WorkflowCheckpoint> findByWorkflowIdOrderByIdDesc(String workflowId) {
        return mapper.history(workflowId);
    }
}
