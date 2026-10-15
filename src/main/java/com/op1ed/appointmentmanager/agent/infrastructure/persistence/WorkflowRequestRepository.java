package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.agent.domain.WorkflowRequest;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.WorkflowRequestMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class WorkflowRequestRepository extends MybatisRepository<WorkflowRequest> {

    private final WorkflowRequestMapper mapper;

    public WorkflowRequestRepository(WorkflowRequestMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<WorkflowRequest> findByOwnerIdAndRequestId(Long ownerId, String requestId) {
        return mapper.findRequest(ownerId, requestId);
    }
}
