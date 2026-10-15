package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.agent.domain.ReschedulingWorkflow;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.ReschedulingWorkflowMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class ReschedulingWorkflowRepository extends MybatisRepository<ReschedulingWorkflow> {

    private final ReschedulingWorkflowMapper mapper;

    public ReschedulingWorkflowRepository(ReschedulingWorkflowMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<ReschedulingWorkflow> findByIdAndOwnerId(String id, Long ownerId) {
        return mapper.findOwned(id, ownerId);
    }

    public Optional<ReschedulingWorkflow> lockOwned(String id, Long ownerId) {
        return mapper.lockOwned(id, ownerId);
    }
}
