package com.op1ed.appointmentmanager.agent.infrastructure.springai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.agent.application.AgentActionResult;
import com.op1ed.appointmentmanager.agent.application.AgentChatPolicy;
import com.op1ed.appointmentmanager.agent.application.AgentMessage;
import com.op1ed.appointmentmanager.agent.application.AgentModel;
import com.op1ed.appointmentmanager.agent.application.AgentModelReply;
import com.op1ed.appointmentmanager.agent.application.AgentRunResult;
import com.op1ed.appointmentmanager.agent.application.AgentRuntime;
import com.op1ed.appointmentmanager.agent.application.AgentToolCatalog;
import com.op1ed.appointmentmanager.agent.application.AgentToolDefinition;
import com.op1ed.appointmentmanager.agent.application.AgentToolExecution;
import com.op1ed.appointmentmanager.agent.application.AgentToolService;
import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingWorkflowView;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

@Component
public class SpringAiAgentRuntime implements AgentRuntime {
    private final AgentModel model;
    private final AgentToolCatalog catalog;
    private final AgentToolService tools;
    private final ObjectMapper json;
    private final AgentChatPolicy policy;

    public SpringAiAgentRuntime(AgentModel model, AgentToolCatalog catalog, AgentToolService tools,
            ObjectMapper json, AgentChatPolicy policy) {
        this.model = model;
        this.catalog = catalog;
        this.tools = tools;
        this.json = json;
        this.policy = policy;
    }

    @Override public boolean available() { return model.available(); }

    @Override
    public AgentRunResult run(UserIdentity caller, List<AgentMessage> messages) {
        // 每轮建立独立运行对象，登录身份与计数不会在账号之间共享。
        Run run = new Run(UserIdentity.requireAuthenticated(caller));
        ReactAgent agent = ReactAgent.builder().name("appointment_assistant")
                .model(new DeepSeekChatModelAdapter(run::complete))
                .tools(catalog.definitions().stream().map(run::callback).toList())
                .hooks(new BudgetHook(run)).interceptors(new ToolGuard(run))
                .parallelToolExecution(false).wrapSyncToolsAsAsync(false).enableLogging(false).build();
        try {
            var config = RunnableConfig.builder().threadId(UUID.randomUUID().toString())
                    .addMetadata("_stream_", false).build();
            AssistantMessage reply = agent.call(SpringAiMessages.toSpring(messages), config);
            if (run.reply == null) {
                run.reply = reply.getText();
            }
        } catch (Exception exception) {
            if (!run.workflows.isEmpty()) { run.workflowPending(); }
            else if (!run.actions.isEmpty()) {
                run.pending();
            } else {
                run.fail("MODEL_ERROR", "Agent 暂时无法完成请求，请稍后重试。");
            }
        }
        return new AgentRunResult(run.status, run.reply, run.actions, run.executions,
                run.modelCalls, run.failureCode, run.generated, run.workflows);
    }

    private final class Run {
        private final UserIdentity caller;
        private final long started = System.nanoTime();
        private final List<AgentActionResult> actions = new ArrayList<>();
        private final List<ReschedulingWorkflowView> workflows = new ArrayList<>();
        private final List<AgentToolExecution> executions = new ArrayList<>();
        private final List<AgentMessage> generated = new ArrayList<>();
        private int modelCalls;
        private int toolCalls;
        private String status = "COMPLETED";
        private String reply;
        private String failureCode;

        Run(UserIdentity caller) { this.caller = caller; }

        Duration remaining() { return policy.timeout().minusNanos(System.nanoTime() - started); }

        AgentModelReply complete(List<AgentMessage> messages) {
            modelCalls++;
            try {
                AgentModelReply response = model.complete(messages, catalog.definitions(), remaining());
                if (response.toolCalls().isEmpty()) {
                    if (response.content() == null || response.content().isBlank()) {
                        fail("MODEL_ERROR", "模型未返回有效回复，请稍后重试。");
                        return new AgentModelReply(reply, List.of());
                    }
                    reply = response.content();
                } else {
                    generated.add(AgentMessage.assistant(response));
                }
                return response;
            } catch (BusinessException exception) {
                if (exception.getCode() == ErrorCode.MODEL_TIMEOUT || exception.getCode() == ErrorCode.MODEL_ERROR) {
                    fail(exception.getCode().name(), exception.getMessage());
                } else {
                    fail("MODEL_ERROR", "模型服务暂时无法完成请求。");
                }
                return new AgentModelReply(reply, List.of());
            }
        }

        ToolCallback callback(AgentToolDefinition tool) {
            return new ToolCallback() {
                @Override public ToolDefinition getToolDefinition() {
                    return ToolDefinition.builder().name(tool.function().name()).description(tool.function().description())
                            .inputSchema(write(tool.function().parameters())).build();
                }
                @Override public String call(String arguments) {
                    try {
                        var input = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(arguments);
                        var result = tools.execute(caller, tool.function().name(), input).result();
                        if (result instanceof AgentActionResult action) {
                            actions.add(action);
                        }
                        if (result instanceof ReschedulingWorkflowView workflow) { workflows.add(workflow); }
                        String output = write(Map.of("ok", true, "result", result));
                        return actions.isEmpty() && workflows.isEmpty() && output.length() > policy.maxToolResultChars()
                                ? error("RESULT_TOO_LARGE", "结果过大，请减少 size 或增加筛选条件") : output;
                    } catch (JsonProcessingException exception) {
                        return error("INVALID_ARGUMENT", "工具参数必须是一个合法的 JSON 对象");
                    } catch (BusinessException exception) {
                        return error(exception.getCode().name(), exception.getMessage());
                    }
                }
                @Override public String call(String arguments, ToolContext context) { return call(arguments); }
            };
        }

        void pending() {
            status = "ACTION_PENDING";
            reply = "已生成操作预览，请核对返回的姓名、医生、时段和变更内容。预约尚未改变，需要通过动作确认接口确认。";
            failureCode = null;
        }

        void workflowPending() {
            status = "WORKFLOW_PENDING";
            reply = "已创建改期流程，请查看候选时段并通过客户端选择、核对预览和确认。当前预约尚未改变。";
            failureCode = null;
        }

        void limit(String detail) { status = "LIMIT_REACHED"; reply = detail; }
        void fail(String code, String detail) { status = "FAILED"; failureCode = code; reply = detail; }
    }

    @HookPositions(HookPosition.BEFORE_MODEL)
    private final class BudgetHook extends ModelHook {
        private final Run run;
        BudgetHook(Run run) { this.run = run; }
        @Override public String getName() { return "appointment_guardrails"; }
        @Override public List<JumpTo> canJumpTo() { return List.of(JumpTo.end); }

        @Override
        public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
            if (!run.workflows.isEmpty()) { run.workflowPending(); }
            else if (!run.actions.isEmpty()) {
                run.pending();
            } else if (run.toolCalls >= policy.maxToolCalls()) {
                run.limit("本轮工具调用已达到上限，请缩小查询范围或补充预约信息后继续。");
            } else if (run.modelCalls >= policy.maxModelCalls()) {
                run.limit("本轮模型调用已达到上限，请补充具体的医生、日期或预约信息后继续。");
            } else {
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
                if (write(SpringAiMessages.fromSpring(messages)).length() + write(catalog.definitions()).length() > policy.maxContextChars()) {
                    run.limit("本轮查询结果较多，请缩小姓名或时间范围后继续。");
                } else if (run.remaining().isNegative() || run.remaining().isZero()) {
                    run.fail("MODEL_TIMEOUT", "本轮模型请求超过时间预算，请稍后重试。");
                } else {
                    return CompletableFuture.completedFuture(Map.of());
                }
            }
            return CompletableFuture.completedFuture(Map.of("jump_to", JumpTo.end, "messages", new AssistantMessage(run.reply)));
        }
    }

    private final class ToolGuard extends ToolInterceptor {
        private final Run run;
        ToolGuard(Run run) { this.run = run; }
        @Override public String getName() { return "appointment_tool_guard"; }

        @Override
        public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
            String output;
            if (!run.workflows.isEmpty()) {
                output = error("SKIPPED_AFTER_WORKFLOW", "已创建改期流程，请等待用户选择和确认");
            } else if (!run.actions.isEmpty()) {
                output = error("SKIPPED_AFTER_PREVIEW", "已生成预览，请先由用户确认");
            } else if (run.toolCalls >= policy.maxToolCalls()) {
                output = error("TOOL_LIMIT_REACHED", "本轮工具调用次数已达到上限");
            } else if (run.remaining().isNegative() || run.remaining().isZero()) {
                output = error("MODEL_TIMEOUT", "本轮时间预算已用完");
            } else {
                run.toolCalls++;
                try {
                    catalog.require(request.getToolName());
                    output = handler.call(request).getResult();
                } catch (BusinessException exception) {
                    output = error(exception.getCode().name(), exception.getMessage());
                } catch (RuntimeException exception) {
                    output = error("INTERNAL_ERROR", "工具暂时无法完成请求");
                }
            }
            try {
                var parsed = json.readTree(output);
                boolean success = parsed.path("ok").asBoolean();
                String code = success ? null : parsed.path("error").path("code").asText("INTERNAL_ERROR");
                run.executions.add(new AgentToolExecution(request.getToolCallId(), request.getToolName(), success, code));
            } catch (JsonProcessingException exception) {
                output = error("INTERNAL_ERROR", "工具暂时无法完成请求");
                run.executions.add(new AgentToolExecution(request.getToolCallId(), request.getToolName(), false, "INTERNAL_ERROR"));
            }
            run.generated.add(AgentMessage.tool(request.getToolCallId(), output));
            return ToolCallResponse.of(request.getToolCallId(), request.getToolName(), output);
        }
    }

    private String error(String code, String detail) {
        return write(Map.of("ok", false, "error", Map.of("code", code, "detail", detail)));
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法生成 Agent 数据", exception); }
    }
}
