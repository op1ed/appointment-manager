package com.op1ed.appointmentmanager.agent.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.agent.domain.AgentConversationTurn;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.AgentConversationTurnMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class AgentConversationTurnRepository extends MybatisRepository<AgentConversationTurn> {

    private final AgentConversationTurnMapper mapper;

    public AgentConversationTurnRepository(AgentConversationTurnMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<AgentConversationTurn> findByConversationIdAndRequestId(String conversationId, String requestId) {
        return mapper.findRequest(conversationId, requestId);
    }

    public List<AgentConversationTurn> findTop10ByConversationIdOrderByTurnNumberDesc(String conversationId) {
        return mapper.recent(conversationId);
    }
}
