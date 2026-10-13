package com.op1ed.appointmentmanager.agent.infrastructure.springai;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.agent.application.workflow.*;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpringAiReschedulingGraphTest {
    private final UserIdentity user = new UserIdentity(1L, "graph", "Graph 用户", false);
    private final String id = UUID.randomUUID().toString();
    private final MemorySaver saver = MemorySaver.builder().build();
    private final ReschedulingWorkflowOperations operations = mock(ReschedulingWorkflowOperations.class);
    private final WorkflowJson json = new WorkflowJson(new ObjectMapper().findAndRegisterModules());
    private SpringAiReschedulingGraph graph;
    private final WorkflowCommands.Start command = new WorkflowCommands.Start(UUID.randomUUID().toString(), 10L, null,
            Instant.parse("2030-01-01T00:00:00Z"), Instant.parse("2030-01-02T00:00:00Z"));

    @BeforeEach void setup() throws Exception {
        when(operations.initial(id, command)).thenReturn(Map.of("workflowId", id, "status", "CREATED", "actionId", ""));
        when(operations.readCurrent(eq(user), anyMap())).thenReturn(Map.of("status", "SEARCHING"));
        when(operations.queryCandidates(anyMap())).thenReturn(Map.of("status", "WAITING_FOR_SELECTION"));
        when(operations.prepare(eq(user), anyMap())).thenReturn(Map.of("status", "WAITING_FOR_CONFIRMATION", "actionId", "preview"));
        when(operations.execute(eq(user), anyMap())).thenReturn(Map.of("status", "COMPLETED"));
        when(operations.reject(eq(user), anyMap())).thenReturn(Map.of("status", "REJECTED"));
        graph = new SpringAiReschedulingGraph(saver, operations, json);
    }

    @Test void shouldPauseSelectPauseAndExecuteOnlyOnExplicitConfirmation() {
        var started = graph.start(user, id, command);
        assertThat(started.status()).isEqualTo("WAITING_FOR_SELECTION");
        assertThat(started.nextNode()).isEqualTo("await_selection");
        verify(operations, never()).prepare(any(), anyMap());
        var selected = graph.resume(user, id, started.nextNode(), WorkflowCommands.Signal.SELECT, 20L, "");
        assertThat(selected.status()).isEqualTo("WAITING_FOR_CONFIRMATION");
        assertThat(selected.nextNode()).isEqualTo("await_confirmation");
        assertThat(WorkflowJson.id(json.state(selected.stateJson()), "selectedSlotId")).isEqualTo(20L);
        verify(operations, never()).execute(any(), anyMap());
        var confirmed = graph.resume(user, id, selected.nextNode(), WorkflowCommands.Signal.CONFIRM, null, "");
        assertThat(confirmed.status()).isEqualTo("COMPLETED");
        assertThat(WorkflowJson.id(json.state(confirmed.stateJson()), "selectedSlotId")).isEqualTo(20L);
        verify(operations).execute(eq(user), anyMap());
        verify(operations, times(1)).readCurrent(eq(user), anyMap());
    }

    @Test void shouldResumeWithNewCompiledGraphUsingExistingCheckpoints() throws Exception {
        var started = graph.start(user, id, command);
        var freshGraph = new SpringAiReschedulingGraph(saver, operations, json);
        var selected = freshGraph.resume(user, id, started.nextNode(), WorkflowCommands.Signal.SELECT, 20L, "");
        assertThat(selected.status()).isEqualTo("WAITING_FOR_CONFIRMATION");
        verify(operations, times(1)).readCurrent(eq(user), anyMap());
    }

    @Test void shouldRouteRefreshBackToReadAndSearch() {
        var started = graph.start(user, id, command);
        var selected = graph.resume(user, id, started.nextNode(), WorkflowCommands.Signal.SELECT, 20L, "");
        var refreshed = graph.resume(user, id, selected.nextNode(), WorkflowCommands.Signal.REFRESH, null, "冲突恢复");
        assertThat(refreshed.nextNode()).isEqualTo("await_selection");
        assertThat(refreshed.status()).isEqualTo("WAITING_FOR_SELECTION");
        verify(operations, times(2)).readCurrent(eq(user), anyMap());
        verify(operations, never()).execute(any(), anyMap());
    }

    @Test void shouldEndRejectedFlowWithoutExecuting() {
        var started = graph.start(user, id, command);
        assertThat(graph.resume(user, id, started.nextNode(), WorkflowCommands.Signal.REJECT, null, "").status()).isEqualTo("REJECTED");
        verify(operations, never()).execute(any(), anyMap());
    }

    @Test void shouldEndWhenAppointmentIsNoLongerBookable() {
        when(operations.readCurrent(eq(user), anyMap())).thenReturn(Map.of("status", "ABORTED"));
        assertThat(graph.start(user, id, command).status()).isEqualTo("ABORTED");
        verify(operations, never()).queryCandidates(anyMap());
    }

    @Test void shouldUnwrapBusinessConflictAcrossFrameworkBoundary() {
        var started = graph.start(user, id, command);
        var conflict = new WorkflowBusinessConflict("时段被占用", new IllegalStateException());
        when(operations.prepare(eq(user), anyMap())).thenThrow(conflict);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> graph.resume(user, id, started.nextNode(),
                WorkflowCommands.Signal.SELECT, 20L, "")).isSameAs(conflict);
    }
}
