package com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper;

import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.op1ed.appointmentmanager.agent.domain.AgentConversation;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {
    @Select("SELECT * FROM agent_conversations WHERE id = #{id} AND owner_id = #{ownerId}")
    Optional<AgentConversation> findOwned(@Param("id") String id, @Param("ownerId") Long ownerId);

    @Select("SELECT * FROM agent_conversations WHERE id = #{id} AND owner_id = #{ownerId} FOR UPDATE")
    Optional<AgentConversation> lockOwned(@Param("id") String id, @Param("ownerId") Long ownerId);
}
