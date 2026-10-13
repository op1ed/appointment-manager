package com.op1ed.appointmentmanager.agent.infrastructure.springai;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.agent.application.AgentActionResult;
import com.op1ed.appointmentmanager.agent.application.AgentChatPolicy;
import com.op1ed.appointmentmanager.agent.application.AgentMessage;
import com.op1ed.appointmentmanager.agent.application.AgentModel;
import com.op1ed.appointmentmanager.agent.application.AgentModelReply;
import com.op1ed.appointmentmanager.agent.application.AgentModelToolCall;
import com.op1ed.appointmentmanager.agent.application.AgentToolCatalog;
import com.op1ed.appointmentmanager.agent.application.AgentToolResult;
import com.op1ed.appointmentmanager.agent.application.AgentToolService;
import com.op1ed.appointmentmanager.agent.application.AgentChatResult;
import com.op1ed.appointmentmanager.agent.application.workflow.ReschedulingWorkflowView;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpringAiAgentRuntimeTest {
    private final AgentModel model = mock(AgentModel.class);
    private final AgentToolService tools = mock(AgentToolService.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final UserIdentity caller = new UserIdentity(42L, "learner", "学习者", false);

    private SpringAiAgentRuntime runtime(int modelLimit, int toolLimit) {
        return new SpringAiAgentRuntime(model, new AgentToolCatalog(), tools, json,
                new AgentChatPolicy(modelLimit, toolLimit, 4, 60000, 24000, Duration.ofSeconds(90), ZoneId.of("Asia/Shanghai")));
    }
    private List<AgentMessage> input() { return List.of(AgentMessage.text("system", "系统提示"), AgentMessage.text("user", "查医生")); }
    private AgentModelReply text(String value) { return new AgentModelReply(value, List.of()); }
    private AgentModelToolCall call(String id, String name, String arguments) { return new AgentModelToolCall(id, name, arguments); }

    @Test
    void shouldRunFrameworkModelToolLoopAndPreserveToolCallPairing() {
        when(model.complete(anyList(), anyList(), any())).thenReturn(
                new AgentModelReply("", List.of(call("query", "get_doctor", "{\"doctorId\":7}"))), text("医生已找到"));
        when(tools.execute(eq(caller), eq("get_doctor"), any())).thenReturn(new AgentToolResult("get_doctor", Map.of("id", 7)));
        var result = runtime(3, 4).run(caller, input());
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.reply()).isEqualTo("医生已找到");
        assertThat(result.modelCalls()).isEqualTo(2);
        assertThat(result.generatedMessages()).hasSize(2);
        assertThat(result.generatedMessages().get(1).toolCallId()).isEqualTo("query");
        verify(tools).execute(eq(caller), eq("get_doctor"), eq(json.valueToTree(Map.of("doctorId", 7))));
    }

    @Test
    void shouldStopAfterOnePreviewAndSkipRemainingCallsWithoutCallingModelAgain() {
        var action = new AgentActionResult("action", "BOOK", "PENDING", Instant.now(), Instant.now().plusSeconds(300),
                null, json.createObjectNode(), null);
        when(model.complete(anyList(), anyList(), any())).thenReturn(new AgentModelReply("已预约成功", List.of(
                call("first", "prepare_booking", "{\"slotId\":1,\"customerName\":\"张三\"}"),
                call("second", "prepare_booking", "{\"slotId\":2,\"customerName\":\"张三\"}"))));
        when(tools.execute(eq(caller), eq("prepare_booking"), any())).thenReturn(new AgentToolResult("prepare_booking", action));
        var result = runtime(3, 4).run(caller, input());
        assertThat(result.status()).isEqualTo("ACTION_PENDING");
        assertThat(result.reply()).contains("预约尚未改变");
        assertThat(result.actions()).containsExactly(action);
        assertThat(result.toolExecutions().get(1).errorCode()).isEqualTo("SKIPPED_AFTER_PREVIEW");
        verify(model, times(1)).complete(anyList(), anyList(), any());
        verify(tools, times(1)).execute(any(), any(), any());
    }

    @Test
    void shouldTurnUnknownToolAndMalformedArgumentsIntoStructuredErrors() {
        when(model.complete(anyList(), anyList(), any())).thenReturn(new AgentModelReply("", List.of(
                call("unknown", "confirm_action", "{}"), call("malformed", "search_doctors", "{} {}"))), text("需要更正"));
        var result = runtime(3, 4).run(caller, input());
        assertThat(result.toolExecutions()).hasSize(2).allSatisfy(trace -> assertThat(trace.errorCode()).isEqualTo("INVALID_ARGUMENT"));
        verifyNoInteractions(tools);
    }

    @Test
    void shouldStopAtModelBudgetUsingHook() {
        when(model.complete(anyList(), anyList(), any())).thenReturn(new AgentModelReply("", List.of(call("query", "search_doctors", "{}"))));
        when(tools.execute(any(), any(), any())).thenReturn(new AgentToolResult("search_doctors", Map.of("content", List.of())));
        var result = runtime(2, 4).run(caller, input());
        assertThat(result.status()).isEqualTo("LIMIT_REACHED");
        assertThat(result.modelCalls()).isEqualTo(2);
        verify(model, times(2)).complete(anyList(), anyList(), any());
    }

    @Test
    void shouldSkipToolsAfterBudgetAndPreserveEveryCallPair() {
        when(model.complete(anyList(), anyList(), any())).thenReturn(new AgentModelReply("", List.of(
                call("one", "search_doctors", "{}"), call("two", "search_doctors", "{}"), call("three", "search_doctors", "{}"))));
        when(tools.execute(any(), any(), any())).thenReturn(new AgentToolResult("search_doctors", Map.of("content", List.of())));
        var result = runtime(3, 2).run(caller, input());
        assertThat(result.status()).isEqualTo("LIMIT_REACHED");
        assertThat(result.generatedMessages()).hasSize(4);
        assertThat(result.toolExecutions().get(2).errorCode()).isEqualTo("TOOL_LIMIT_REACHED");
        verify(tools, times(2)).execute(any(), any(), any());
    }

    @Test
    void shouldKeepProviderFailureStatusInsteadOfFrameworkExceptionText() {
        when(model.complete(anyList(), anyList(), any())).thenThrow(new BusinessException(ErrorCode.MODEL_TIMEOUT, "模型超时"));
        var result = runtime(3, 4).run(caller, input());
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.failureCode()).isEqualTo("MODEL_TIMEOUT");
        assertThat(result.reply()).isEqualTo("模型超时");
        verifyNoInteractions(tools);
    }

    @Test void shouldStopAfterWorkflowCreationAndExposeServerWorkflow() {
        var workflow = new ReschedulingWorkflowView("workflow", 1, "WAITING_FOR_SELECTION", "await_selection",
                10L, 11L, 7L, "医生", Instant.now(), Instant.now().plusSeconds(3600), json.createObjectNode(),
                List.of(), null, null, "请选择", Instant.now(), Instant.now());
        when(model.complete(anyList(), anyList(), any())).thenReturn(new AgentModelReply("改期成功", List.of(
                call("start", "start_rescheduling_workflow", "{\"appointmentId\":10,\"from\":\"2030-01-01T00:00:00Z\",\"to\":\"2030-01-02T00:00:00Z\"}"),
                call("extra", "prepare_cancellation", "{\"appointmentId\":10}"))));
        when(tools.execute(eq(caller), eq("start_rescheduling_workflow"), any())).thenReturn(new AgentToolResult("start_rescheduling_workflow", workflow));
        var result = runtime(3, 4).run(caller, input());
        assertThat(result.status()).isEqualTo("WORKFLOW_PENDING");
        assertThat(result.reply()).contains("当前预约尚未改变");
        assertThat(result.workflows()).containsExactly(workflow);
        assertThat(result.toolExecutions().get(1).errorCode()).isEqualTo("SKIPPED_AFTER_WORKFLOW");
        verify(model, times(1)).complete(anyList(), anyList(), any());
        verify(tools, times(1)).execute(any(), any(), any());
    }

    @Test void shouldReadChatResponsesSavedBeforeWorkflowFieldWasIntroduced() throws Exception {
        var legacy = json.readValue("{\"conversationId\":\"old\",\"requestId\":\"old-request\",\"status\":\"COMPLETED\","
                + "\"reply\":\"旧回复\",\"actions\":[],\"toolExecutions\":[],\"modelCalls\":1,\"failureCode\":null}", AgentChatResult.class);
        assertThat(legacy.workflows()).isEmpty();
        assertThat(legacy.reply()).isEqualTo("旧回复");
    }
}
