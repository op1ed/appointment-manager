package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.Optional;

import com.op1ed.appointmentmanager.agent.domain.AgentConversation;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.AgentConversationMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class AgentConversationRepository extends MybatisRepository<AgentConversation> {

    private final AgentConversationMapper mapper;

    public AgentConversationRepository(AgentConversationMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<AgentConversation> findByIdAndOwnerId(String id, Long ownerId) {
        return mapper.findOwned(id, ownerId);
    }

    public Optional<AgentConversation> findByIdAndOwnerIdForUpdate(String id, Long ownerId) {
        return mapper.lockOwned(id, ownerId);
    }
}
