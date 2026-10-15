package com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.op1ed.appointmentmanager.agent.domain.AgentConversationTurn;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface AgentConversationTurnMapper extends BaseMapper<AgentConversationTurn> {
    @Select("SELECT * FROM agent_conversation_turns WHERE conversation_id = #{conversationId} AND request_id = #{requestId}")
    Optional<AgentConversationTurn> findRequest(@Param("conversationId") String conversationId, @Param("requestId") String requestId);

    @Select("SELECT * FROM agent_conversation_turns WHERE conversation_id = #{conversationId} ORDER BY turn_number DESC LIMIT 10")
    List<AgentConversationTurn> recent(@Param("conversationId") String conversationId);
}
