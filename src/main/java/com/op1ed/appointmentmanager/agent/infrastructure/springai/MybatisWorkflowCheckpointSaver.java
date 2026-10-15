package com.op1ed.appointmentmanager.agent.infrastructure.springai;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.agent.domain.WorkflowCheckpoint;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.WorkflowCheckpointMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.op1ed.appointmentmanager.shared.persistence.PersistenceChecks.requireUpdatedRow;

/** 无进程内缓存；检查点与预约、动作、请求日志共用 Spring 事务。 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class MybatisWorkflowCheckpointSaver implements BaseCheckpointSaver {
    private final WorkflowCheckpointMapper checkpoints;
    private final ObjectMapper json;
    public MybatisWorkflowCheckpointSaver(WorkflowCheckpointMapper checkpoints, ObjectMapper json) {
        this.checkpoints = checkpoints; this.json = json;
    }
    @Override public Collection<Checkpoint> list(RunnableConfig config) {
        return checkpoints.history(thread(config)).stream().map(this::decode).toList();
    }
    @Override public Optional<Checkpoint> get(RunnableConfig config) {
        var row = config.checkPointId().map(id -> checkpoints.findCheckpoint(thread(config), id))
                .orElseGet(() -> checkpoints.latest(thread(config)));
        return row.map(this::decode);
    }
    @Override public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        String state = json.writeValueAsString(checkpoint.getState());
        var existing = checkpoints.findCheckpoint(thread(config), checkpoint.getId());
        WorkflowCheckpoint row = existing.orElseGet(() -> new WorkflowCheckpoint(checkpoint.getId(), thread(config), checkpoint.getNodeId(),
                        checkpoint.getNextNodeId(), state));
        row.update(checkpoint.getNodeId(), checkpoint.getNextNodeId(), state);
        if (existing.isPresent()) { requireUpdatedRow(checkpoints.updateById(row)); }
        else { checkpoints.insert(row); }
        return RunnableConfig.builder(config).checkPointId(checkpoint.getId()).build();
    }
    @Override public Tag release(RunnableConfig config) {
        // releaseThread(false)；保留审计数据，即使框架调用 release 也不删除。
        return new Tag(thread(config), list(config));
    }
    private String thread(RunnableConfig config) { return config.threadId().orElseThrow(); }
    private Checkpoint decode(WorkflowCheckpoint row) {
        try {
            Map<String, Object> state = json.readValue(row.getStateJson(), new TypeReference<>() { });
            return Checkpoint.builder().id(row.getCheckpointId()).state(state).nodeId(row.getNodeId())
                    .nextNodeId(row.getNextNodeId()).build();
        } catch (Exception exception) { throw new IllegalStateException("无法读取工作流检查点", exception); }
    }
}
