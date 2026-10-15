package com.op1ed.appointmentmanager.agent.infrastructure.springai;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingGraph;
import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingWorkflowOperations;
import com.op1ed.appointmentmanager.agent.application.workflow.WorkflowBusinessConflict;
import com.op1ed.appointmentmanager.agent.application.workflow.WorkflowCommands;
import com.op1ed.appointmentmanager.agent.application.workflow.WorkflowJson;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.*;
import static com.op1ed.appointmentmanager.agent.application.workflow.WorkflowJson.*;

@Component
public class SpringAiReschedulingGraph implements ReschedulingGraph {
    private final CompiledGraph graph;
    private final ReschedulingWorkflowOperations operations;
    private final WorkflowJson json;

    @org.springframework.beans.factory.annotation.Autowired
    public SpringAiReschedulingGraph(MybatisWorkflowCheckpointSaver saver, ReschedulingWorkflowOperations operations,
            WorkflowJson json) throws Exception {
        this((BaseCheckpointSaver) saver, operations, json);
    }
    // 显式构造也供无数据库的框架行为测试使用。
    public SpringAiReschedulingGraph(BaseCheckpointSaver saver, ReschedulingWorkflowOperations operations,
            WorkflowJson json) throws Exception {
        this.operations = operations; this.json = json;
        // input(checkpoint) 仅恢复注册过的键；显式登记完整状态，避免恢复时丢失 signal 和业务参数。
        StateGraph definition = new StateGraph(() -> List.of("workflowId", "appointmentId", "doctorId", "doctorName",
                "from", "to", "status", "note", "actionId", "candidatesJson", "selectedSlotId", "expectedSlotId",
                "appointmentJson", "signal").stream().collect(Collectors.toMap(key -> key, key -> KeyStrategy.REPLACE)));
        definition.addNode("read_current", node(operations::readCurrent))
                .addNode("query_candidates", node((caller, state) -> operations.queryCandidates(state)))
                .addNode("await_selection", node((caller, state) -> Map.of()))
                .addNode("prepare_preview", node(operations::prepare))
                .addNode("await_confirmation", node((caller, state) -> Map.of()))
                .addNode("execute", node(operations::execute))
                .addNode("reject", node(operations::reject))
                .addEdge(START, "read_current")
                .addConditionalEdges("read_current", state -> CompletableFuture.completedFuture(
                        text(state.data(), "status").equals("ABORTED") ? "end" : "search"),
                        Map.of("end", END, "search", "query_candidates"))
                .addEdge("query_candidates", "await_selection")
                .addConditionalEdges("await_selection", state -> CompletableFuture.completedFuture(text(state.data(), "signal")),
                        Map.of("SELECT", "prepare_preview", "REFRESH", "read_current", "REJECT", "reject"))
                .addEdge("prepare_preview", "await_confirmation")
                .addConditionalEdges("await_confirmation", state -> CompletableFuture.completedFuture(text(state.data(), "signal")),
                        Map.of("CONFIRM", "execute", "REFRESH", "read_current", "REJECT", "reject"))
                .addEdge("execute", END).addEdge("reject", END);
        this.graph = definition.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(saver).build())
                .interruptBefore("await_selection", "await_confirmation").releaseThread(false).recursionLimit(30).build());
    }

    private AsyncNodeActionWithConfig node(BiFunction<UserIdentity, Map<String, Object>, Map<String, Object>> action) {
        // 完成的 Future 在调用线程上执行，保留 Spring 事务；这里不调模型、也不并行调业务服务。
        return (state, config) -> CompletableFuture.completedFuture(action.apply(
                (UserIdentity) config.metadata("caller").orElseThrow(), state.data()));
    }
    @Override public GraphResult start(UserIdentity caller, String workflowId, WorkflowCommands.Start command) {
        try {
            graph.invoke(operations.initial(workflowId, command), config(caller, workflowId));
            return snapshot(caller, workflowId);
        } catch (Exception exception) { throw propagate(exception); }
    }
    @Override public GraphResult resume(UserIdentity caller, String workflowId, String nextNode,
            WorkflowCommands.Signal signal, Long targetSlotId, String note) {
        try {
            var update = new LinkedHashMap<String, Object>();
            update.put("signal", signal.name()); update.put("note", note);
            if (signal == WorkflowCommands.Signal.SELECT) { update.put("selectedSlotId", targetSlotId); }
            var config = graph.updateState(config(caller, workflowId), update, nextNode);
            // 1.1.2.2 从 checkpoint.nextNode 恢复，继续执行暂停的等待节点，再按 signal 分支。
            graph.invoke((Map<String, Object>) null, config);
            return snapshot(caller, workflowId);
        } catch (Exception exception) { throw propagate(exception); }
    }
    private GraphResult snapshot(UserIdentity caller, String workflowId) throws Exception {
        var snapshot = graph.getState(config(caller, workflowId));
        var state = snapshot.state().data();
        return new GraphResult(json.write(state), text(state, "status"), snapshot.next(), text(state, "actionId"));
    }
    private RunnableConfig config(UserIdentity caller, String workflowId) {
        return RunnableConfig.builder().threadId(workflowId).addMetadata("caller", caller).build();
    }
    private RuntimeException propagate(Exception exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof WorkflowBusinessConflict conflict) { return conflict; }
            if (cause instanceof BusinessException business) { return business; }
            cause = cause.getCause();
        }
        return new IllegalStateException("改期流程执行失败", exception);
    }
}
