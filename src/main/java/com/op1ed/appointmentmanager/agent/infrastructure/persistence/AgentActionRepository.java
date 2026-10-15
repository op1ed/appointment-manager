package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.agent.domain.AgentAction;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.AgentActionMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class AgentActionRepository extends MybatisRepository<AgentAction> {

    private final AgentActionMapper mapper;

    public AgentActionRepository(AgentActionMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<AgentAction> findByIdAndOwnerId(String id, Long ownerId) {
        return mapper.findOwned(id, ownerId);
    }

    public Optional<AgentAction> findByIdAndOwnerIdForUpdate(String id, Long ownerId) {
        return mapper.lockOwned(id, ownerId);
    }
}
