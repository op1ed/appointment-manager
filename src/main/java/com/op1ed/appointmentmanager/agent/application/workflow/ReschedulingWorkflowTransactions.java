package com.op1ed.appointmentmanager.agent.application.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.op1ed.appointmentmanager.agent.application.AgentActionService;
import com.op1ed.appointmentmanager.agent.domain.ReschedulingWorkflow;
import com.op1ed.appointmentmanager.agent.domain.WorkflowRequest;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.ReschedulingWorkflowMapper;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.mapper.WorkflowRequestMapper;
import com.op1ed.appointmentmanager.appointment.application.SlotResult;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import static com.op1ed.appointmentmanager.agent.application.workflow.WorkflowJson.*;
import static com.op1ed.appointmentmanager.shared.persistence.PersistenceChecks.requireUpdatedRow;

/** 每次 Graph 推进都是短事务，检查点、动作、预约变更和响应日志一起提交。 */
@Service
public class ReschedulingWorkflowTransactions {
    private final ReschedulingWorkflowMapper workflows;
    private final WorkflowRequestMapper requests;
    private final ReschedulingGraph graph;
    private final ReschedulingWorkflowOperations operations;
    private final AgentActionService actions;
    private final WorkflowJson json;
    public ReschedulingWorkflowTransactions(ReschedulingWorkflowMapper workflows, WorkflowRequestMapper requests,
            ReschedulingGraph graph, ReschedulingWorkflowOperations operations, AgentActionService actions, WorkflowJson json) {
        this.workflows = workflows; this.requests = requests; this.graph = graph;
        this.operations = operations; this.actions = actions; this.json = json;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReschedulingWorkflowView start(UserIdentity user, WorkflowCommands.Start command) {
        var previous = replay(user, command.requestId(), null, "START", command);
        if (previous.isPresent()) { return previous.get(); }
        operations.validateStart(user, command);
        var workflow = new ReschedulingWorkflow(UUID.randomUUID().toString(), user.id(),
                command.appointmentId(), Instant.now());
        workflows.insert(workflow);
        return store(user, workflow, command.requestId(), "START", command, graph.start(user, workflow.getId(), command));
    }

    @Transactional(readOnly = true)
    public Optional<ReschedulingWorkflowView> replayStart(UserIdentity user, WorkflowCommands.Start command) {
        return replay(user, command.requestId(), null, "START", command);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReschedulingWorkflowView advance(UserIdentity user, String id, WorkflowCommands.Signal signal,
            WorkflowCommands.Step command) {
        return advance(user, id, signal, command, false, "");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReschedulingWorkflowView recover(UserIdentity user, String id, WorkflowCommands.Signal signal,
            WorkflowCommands.Step command, String note) {
        return advance(user, id, signal, command, true, note);
    }

    private ReschedulingWorkflowView advance(UserIdentity user, String id, WorkflowCommands.Signal signal,
            WorkflowCommands.Step command, boolean recovery, String note) {
        // 第一条查询是 owner + PK 的当前读；跨实例也能串行推进同一个流程。
        var workflow = workflows.lockOwned(id, user.id()).orElseThrow(this::notFound);
        var previous = replay(user, command.requestId(), id, signal.name(), command);
        if (previous.isPresent()) { return previous.get(); }
        if (workflow.getRevision() != command.expectedRevision()) {
            throw new BusinessException(ErrorCode.CONFLICT, "流程已更新，请刷新后使用最新修订号");
        }
        if (workflow.finished()) { throw new BusinessException(ErrorCode.CONFLICT, "流程已结束"); }
        if (signal == WorkflowCommands.Signal.SELECT) {
            if (!workflow.getStatus().equals("WAITING_FOR_SELECTION") || command.targetSlotId() == null) {
                throw new BusinessException(ErrorCode.CONFLICT, "当前流程不能选择时段");
            }
        } else {
            if (command.targetSlotId() != null) { throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "此操作不接受目标时段"); }
            if (signal == WorkflowCommands.Signal.CONFIRM && !workflow.getStatus().equals("WAITING_FOR_CONFIRMATION")) {
                throw new BusinessException(ErrorCode.CONFLICT, "请先选择时段并核对预览");
            }
        }
        var result = graph.resume(user, id, workflow.getNextNode(), recovery ? WorkflowCommands.Signal.REFRESH : signal,
                recovery ? null : command.targetSlotId(), note);
        return store(user, workflow, command.requestId(), signal.name(), command, result);
    }

    @Transactional(readOnly = true)
    public ReschedulingWorkflowView find(UserIdentity user, String id) {
        return view(user, workflows.findOwned(id, user.id()).orElseThrow(this::notFound));
    }

    private ReschedulingWorkflowView store(UserIdentity user, ReschedulingWorkflow workflow, String requestId,
            String kind, Object command, ReschedulingGraph.GraphResult result) {
        workflow.advance(result.stateJson(), result.status(), result.nextNode(), Instant.now());
        requireUpdatedRow(workflows.updateById(workflow));
        var response = view(user, workflow);
        requests.insert(new WorkflowRequest(user.id(), requestId, workflow.getId(), kind, json.write(command), json.write(response)));
        return response;
    }

    private Optional<ReschedulingWorkflowView> replay(UserIdentity user, String requestId, String workflowId,
            String kind, Object command) {
        return requests.findRequest(user.id(), requestId).map(previous -> {
            if (!previous.getKind().equals(kind) || workflowId != null && !workflowId.equals(previous.getWorkflowId())
                    || !json.read(previous.getPayloadJson(), JsonNode.class).equals(json.read(json.write(command), JsonNode.class))) {
                throw new BusinessException(ErrorCode.CONFLICT, "此请求 ID 已用于其他流程或参数");
            }
            return json.read(previous.getResponseJson(), ReschedulingWorkflowView.class);
        });
    }

    private ReschedulingWorkflowView view(UserIdentity user, ReschedulingWorkflow workflow) {
        var state = json.state(workflow.getStateJson());
        String actionId = text(state, "actionId");
        List<SlotResult> candidates = json.read(text(state, "candidatesJson"), new TypeReference<>() { });
        return new ReschedulingWorkflowView(workflow.getId(), workflow.getRevision(), workflow.getStatus(), workflow.getNextNode(),
                workflow.getAppointmentId(), id(state, "expectedSlotId"), id(state, "doctorId"), text(state, "doctorName"),
                Instant.parse(text(state, "from")), Instant.parse(text(state, "to")),
                json.read(text(state, "appointmentJson"), JsonNode.class), candidates, id(state, "selectedSlotId"),
                actionId.isEmpty() ? null : actions.find(user, actionId), text(state, "note"), workflow.getCreatedAt(), workflow.getUpdatedAt());
    }
    private BusinessException notFound() { return new BusinessException(ErrorCode.NOT_FOUND, "改期流程不存在"); }
}
