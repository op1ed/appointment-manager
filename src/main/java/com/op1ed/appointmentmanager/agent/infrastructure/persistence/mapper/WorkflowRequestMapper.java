package com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper;

import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.op1ed.appointmentmanager.agent.domain.WorkflowRequest;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface WorkflowRequestMapper extends BaseMapper<WorkflowRequest> {
    @Select("SELECT * FROM agent_workflow_requests WHERE owner_id = #{ownerId} AND request_id = #{requestId}")
    Optional<WorkflowRequest> findRequest(@Param("ownerId") Long ownerId, @Param("requestId") String requestId);
}
