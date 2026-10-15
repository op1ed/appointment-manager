package com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.op1ed.appointmentmanager.agent.domain.WorkflowCheckpoint;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 继承 BaseMapper 的通用增删改查，下面的方法声明本模块需要的 SQL。 */
@Mapper
public interface WorkflowCheckpointMapper extends BaseMapper<WorkflowCheckpoint> {
    @Select("SELECT * FROM agent_workflow_checkpoints WHERE workflow_id = #{workflowId} AND checkpoint_id = #{checkpointId}")
    Optional<WorkflowCheckpoint> findCheckpoint(@Param("workflowId") String workflowId, @Param("checkpointId") String checkpointId);

    @Select("SELECT * FROM agent_workflow_checkpoints WHERE workflow_id = #{workflowId} ORDER BY id DESC LIMIT 1")
    Optional<WorkflowCheckpoint> latest(@Param("workflowId") String workflowId);

    @Select("SELECT * FROM agent_workflow_checkpoints WHERE workflow_id = #{workflowId} ORDER BY id DESC")
    List<WorkflowCheckpoint> history(@Param("workflowId") String workflowId);
}
