package com.op1ed.appointmentmanager.agent;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.op1ed.appointmentmanager.agent.application.AgentActionService;
import com.op1ed.appointmentmanager.agent.application.workflow.*;
import com.op1ed.appointmentmanager.agent.infrastructure.persistence.*;
import com.op1ed.appointmentmanager.agent.infrastructure.springai.JpaWorkflowCheckpointSaver;
import com.op1ed.appointmentmanager.agent.infrastructure.springai.SpringAiReschedulingGraph;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.support.AgentApiFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReschedulingWorkflowControllerTest extends AgentApiFixtures {
    private static final String ROOT = "/api/agent/rescheduling-workflows";
    @MockitoSpyBean private SpringAiReschedulingGraph graph;
    @Autowired private JpaWorkflowCheckpointSaver saver;
    @Autowired private ReschedulingWorkflowOperations operations;
    @Autowired private WorkflowJson workflowJson;
    @Autowired private ReschedulingWorkflowRepository workflows;
    @Autowired private WorkflowRequestRepository requests;
    @Autowired private AgentActionService actions;
    @Autowired private PlatformTransactionManager transactionManager;

    private String uuid() { return UUID.randomUUID().toString(); }
    private String workflowPath(JsonNode workflow) { return ROOT + "/" + workflow.path("id").asText(); }
    private Map<String, Object> stepBody(JsonNode workflow) {
        return Map.of("requestId", uuid(), "expectedRevision", workflow.path("revision").asLong());
    }
    private JsonNode start(TestRestTemplate client, long appointmentId, String from, String to) {
        var response = client.postForEntity(ROOT, Map.of("requestId", uuid(), "appointmentId", appointmentId,
                "from", from, "to", to), JsonNode.class);
        assertThat(response.getStatusCode()).as("%s", response.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo(workflowPath(response.getBody()));
        return response.getBody();
    }
    private JsonNode selection(TestRestTemplate client, JsonNode workflow, long slotId) {
        var response = client.postForEntity(workflowPath(workflow) + "/selection", Map.of("requestId", uuid(),
                "expectedRevision", workflow.path("revision").asLong(), "targetSlotId", slotId), JsonNode.class);
        assertThat(response.getStatusCode()).as("%s", response.getBody()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }
    private JsonNode step(TestRestTemplate client, JsonNode workflow, String signal) {
        var response = client.postForEntity(workflowPath(workflow) + "/" + signal, stepBody(workflow), JsonNode.class);
        assertThat(response.getStatusCode()).as("%s", response.getBody()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    @Test void shouldPauseForSelectionThenPreviewAndConfirmWithoutModel() {
        var slots = createSlots(3); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(2).endTime().toString());
        assertThat(started.path("status").asText()).isEqualTo("WAITING_FOR_SELECTION");
        assertThat(started.path("nextNode").asText()).isEqualTo("await_selection");
        assertThat(started.path("candidates")).hasSize(2);
        var selected = selection(owner.client(), started, slots.get(1).id());
        assertThat(selected.path("status").asText()).isEqualTo("WAITING_FOR_CONFIRMATION");
        assertThat(selected.path("action").path("workflowId").asText()).isEqualTo(started.path("id").asText());
        assertThat(detail(owner.client(), appointment.id()).slotId()).isEqualTo(slots.get(0).id());
        var completed = step(owner.client(), selected, "confirm");
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("action").path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(detail(owner.client(), appointment.id()).slotId()).isEqualTo(slots.get(1).id());
        assertThat(history(owner.client(), appointment.id()).totalElements()).isEqualTo(2);
    }

    @Test void shouldReplayExactStartRequestAndRejectChangedParameters() {
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        String requestId = uuid();
        var command = Map.of("requestId", requestId, "appointmentId", appointment.id(),
                "from", slots.get(0).startTime().toString(), "to", slots.get(1).endTime().toString());
        var first = owner.client().postForEntity(ROOT, command, JsonNode.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(owner.client().postForEntity(ROOT, command, JsonNode.class).getBody()).isEqualTo(first.getBody());
        assertAgentProblem(owner.client().postForEntity(ROOT, Map.of("requestId", requestId, "appointmentId", appointment.id(),
                "from", slots.get(1).startTime().toString(), "to", slots.get(1).endTime().toString()), JsonNode.class), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM agent_rescheduling_workflows WHERE appointment_id = ?", Long.class, appointment.id())).isEqualTo(1);
    }

    @Test void shouldReplayConfirmedRequestButRejectStaleOrDifferentRequest() {
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(1).endTime().toString());
        var selected = selection(owner.client(), started, slots.get(1).id()); var command = stepBody(selected);
        var confirmed = owner.client().postForEntity(workflowPath(selected) + "/confirm", command, JsonNode.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(owner.client().postForEntity(workflowPath(selected) + "/confirm", command, JsonNode.class).getBody()).isEqualTo(confirmed.getBody());
        assertAgentProblem(owner.client().postForEntity(workflowPath(selected) + "/confirm", stepBody(selected), JsonNode.class), HttpStatus.CONFLICT, "CONFLICT");
        assertAgentProblem(owner.client().postForEntity(workflowPath(selected) + "/refresh", command, JsonNode.class), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(history(owner.client(), appointment.id()).totalElements()).isEqualTo(2);
    }

    @Test void shouldHideOtherOwnersWorkflowEvenFromAdministrator() {
        var slots = createSlots(2); var owner = normalUser(); var stranger = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(1).endTime().toString());
        for (var client : java.util.List.of(stranger.client(), restTemplate)) {
            assertAgentProblem(client.getForEntity(workflowPath(started), JsonNode.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
            assertAgentProblem(client.postForEntity(workflowPath(started) + "/reject", stepBody(started), JsonNode.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        assertAgentProblem(anonymousClient().getForEntity(workflowPath(started), JsonNode.class), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertAgentProblem(stranger.client().postForEntity(ROOT, Map.of("requestId", uuid(), "appointmentId", appointment.id(),
                "from", slots.get(0).startTime().toString(), "to", slots.get(1).endTime().toString()), JsonNode.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test void shouldRejectSelectionOutsideSavedCandidatesAndEarlyConfirmation() {
        var slots = createSlots(3); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(1).endTime().toString());
        assertAgentProblem(owner.client().postForEntity(workflowPath(started) + "/selection", Map.of("requestId", uuid(),
                "expectedRevision", started.path("revision").asLong(), "targetSlotId", slots.get(2).id()), JsonNode.class), HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        assertAgentProblem(owner.client().postForEntity(workflowPath(started) + "/confirm", stepBody(started), JsonNode.class), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(owner.client().getForEntity(workflowPath(started), JsonNode.class).getBody()).isEqualTo(started);
    }

    @Test void shouldBlockBypassingWorkflowConfirmationAndRevokeOnRefresh() {
        var slots = createSlots(3); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(2).endTime().toString());
        var selected = selection(owner.client(), started, slots.get(1).id());
        assertAgentProblem(confirm(owner.client(), selected.path("action")), HttpStatus.CONFLICT, "CONFLICT");
        var refreshed = step(owner.client(), selected, "refresh");
        assertThat(refreshed.path("status").asText()).isEqualTo("WAITING_FOR_SELECTION");
        assertThat(refreshed.path("action").isNull()).isTrue();
        assertThat(owner.client().getForEntity(actionPath(selected.path("action")), JsonNode.class).getBody().path("status").asText()).isEqualTo("REVOKED");
        assertThat(detail(owner.client(), appointment.id()).slotId()).isEqualTo(slots.get(0).id());
    }

    @Test void shouldRecoverIfCandidateIsTakenBeforeSelection() {
        var slots = createSlots(3); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(2).endTime().toString());
        book(restTemplate, slots.get(1).id());
        var recovered = selection(owner.client(), started, slots.get(1).id());
        assertThat(recovered.path("status").asText()).isEqualTo("WAITING_FOR_SELECTION");
        assertThat(recovered.path("candidates")).hasSize(1);
        assertThat(recovered.path("candidates").get(0).path("id").asLong()).isEqualTo(slots.get(2).id());
        assertThat(detail(owner.client(), appointment.id()).slotId()).isEqualTo(slots.get(0).id());
        assertThat(history(owner.client(), appointment.id()).totalElements()).isEqualTo(1);
    }

    @Test void shouldRollbackConflictThenRecoverAndRequireNewConfirmation() {
        var slots = createSlots(3); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(2).endTime().toString());
        var selected = selection(owner.client(), started, slots.get(1).id()); book(restTemplate, slots.get(1).id());
        var command = stepBody(selected);
        var recovered = owner.client().postForEntity(workflowPath(selected) + "/confirm", command, JsonNode.class);
        assertThat(recovered.getStatusCode()).as("%s", recovered.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(recovered.getBody().path("status").asText()).isEqualTo("WAITING_FOR_SELECTION");
        assertThat(owner.client().postForEntity(workflowPath(selected) + "/confirm", command, JsonNode.class).getBody()).isEqualTo(recovered.getBody());
        assertThat(owner.client().getForEntity(actionPath(selected.path("action")), JsonNode.class).getBody().path("status").asText()).isEqualTo("REVOKED");
        assertThat(detail(owner.client(), appointment.id()).slotId()).isEqualTo(slots.get(0).id());
        var reselected = selection(owner.client(), recovered.getBody(), slots.get(2).id());
        assertThat(reselected.path("action").path("id")).isNotEqualTo(selected.path("action").path("id"));
        assertThat(step(owner.client(), reselected, "confirm").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(history(owner.client(), appointment.id()).totalElements()).isEqualTo(2);
    }

    @Test void shouldRecoverExpiredPreviewWithoutChangingAppointment() {
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(1).endTime().toString());
        var selected = selection(owner.client(), started, slots.get(1).id());
        db.update("UPDATE agent_actions SET created_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE), expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 5 MINUTE) WHERE id = ?", selected.path("action").path("id").asText());
        assertThat(step(owner.client(), selected, "confirm").path("status").asText()).isEqualTo("WAITING_FOR_SELECTION");
        assertThat(detail(owner.client(), appointment.id()).slotId()).isEqualTo(slots.get(0).id());
    }

    @Test void shouldRefreshChangedAppointmentAndAbortCancelledAppointment() {
        var slots = createSlots(3); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(2).endTime().toString());
        var selected = selection(owner.client(), started, slots.get(1).id());
        assertThat(reschedule(owner.client(), appointment, slots.get(2).id()).getStatusCode()).isEqualTo(HttpStatus.OK);
        var refreshed = step(owner.client(), selected, "confirm");
        assertThat(refreshed.path("expectedSlotId").asLong()).isEqualTo(slots.get(2).id());
        assertThat(refreshed.path("status").asText()).isEqualTo("WAITING_FOR_SELECTION");
        assertThat(owner.client().postForEntity(path(appointment.id()) + "/cancel", null, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(step(owner.client(), refreshed, "refresh").path("status").asText()).isEqualTo("ABORTED");
    }

    @Test void shouldRejectPreviewWithoutChangingReservation() {
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var selected = selection(owner.client(), start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(1).endTime().toString()), slots.get(1).id());
        assertThat(step(owner.client(), selected, "reject").path("status").asText()).isEqualTo("REJECTED");
        assertThat(owner.client().getForEntity(actionPath(selected.path("action")), JsonNode.class).getBody().path("status").asText()).isEqualTo("REVOKED");
        assertThat(detail(owner.client(), appointment.id()).slotId()).isEqualTo(slots.get(0).id());
    }

    @Test void shouldPauseWithoutCandidatesAndAllowRefreshOrReject() {
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(0).endTime().toString());
        assertThat(started.path("status").asText()).isEqualTo("NO_CANDIDATES");
        assertThat(started.path("candidates")).isEmpty();
        assertThat(step(owner.client(), step(owner.client(), started, "refresh"), "reject").path("status").asText()).isEqualTo("REJECTED");
    }

    @Test void shouldRejectInvalidTimeRangeAndMissingRevision() {
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        assertAgentProblem(owner.client().postForEntity(ROOT, Map.of("requestId", uuid(), "appointmentId", appointment.id(),
                "from", slots.get(0).startTime().toString(), "to", slots.get(0).startTime().plusDays(32).toString()), JsonNode.class), HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(1).endTime().toString());
        assertAgentProblem(owner.client().postForEntity(workflowPath(started) + "/confirm", Map.of("requestId", uuid()), JsonNode.class), HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
    }

    @Test void shouldSerializeConcurrentConfirmationsAndExecuteOnce() throws Exception {
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var selected = selection(owner.client(), start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(1).endTime().toString()), slots.get(1).id());
        var command = stepBody(selected);
        var responses = sendTogether(() -> owner.client().postForEntity(workflowPath(selected) + "/confirm", command, String.class),
                () -> owner.client().postForEntity(workflowPath(selected) + "/confirm", command, String.class));
        assertThat(responses).allSatisfy(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(json.readTree(responses.get(0).getBody())).isEqualTo(json.readTree(responses.get(1).getBody()));
        assertThat(history(owner.client(), appointment.id()).totalElements()).isEqualTo(2);
    }

    @Test void shouldRollbackAppointmentActionCheckpointAndRequestWhenStepFailsAfterExecution() {
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var selected = selection(owner.client(), start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(1).endTime().toString()), slots.get(1).id());
        String id = selected.path("id").asText();
        long count = db.queryForObject("SELECT COUNT(*) FROM agent_workflow_checkpoints WHERE workflow_id = ?", Long.class, id);
        var command = stepBody(selected);
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("模拟执行后持久化失败"); })
                .when(graph).resume(any(), eq(id), anyString(), eq(WorkflowCommands.Signal.CONFIRM), isNull(), anyString());
        try {
            assertThat(owner.client().postForEntity(workflowPath(selected) + "/confirm", command, JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        } finally { reset(graph); }
        assertThat(owner.client().getForEntity(workflowPath(selected), JsonNode.class).getBody()).isEqualTo(selected);
        assertThat(detail(owner.client(), appointment.id()).slotId()).isEqualTo(slots.get(0).id());
        assertThat(history(owner.client(), appointment.id()).totalElements()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM agent_workflow_checkpoints WHERE workflow_id = ?", Long.class, id)).isEqualTo(count);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM agent_workflow_requests WHERE request_id = ?", Long.class, command.get("requestId"))).isZero();
        assertThat(owner.client().postForEntity(workflowPath(selected) + "/confirm", command, JsonNode.class).getBody().path("status").asText()).isEqualTo("COMPLETED");
    }

    @Test void shouldResumeDatabaseCheckpointWithEntirelyNewGraphSaverAndService() throws Exception {
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var started = start(owner.client(), appointment.id(), slots.get(0).startTime().toString(), slots.get(1).endTime().toString());
        var freshSaver = new JpaWorkflowCheckpointSaver(checkpoints(), json);
        var freshGraph = new SpringAiReschedulingGraph(freshSaver, operations, workflowJson);
        var freshTransactions = new ReschedulingWorkflowTransactions(workflows, requests, freshGraph, operations, actions, workflowJson);
        var identity = new UserIdentity(owner.identity().id(), owner.identity().username(), owner.identity().displayName(), false);
        var resumed = new TransactionTemplate(transactionManager).execute(tx -> freshTransactions.advance(identity, started.path("id").asText(),
                WorkflowCommands.Signal.SELECT, new WorkflowCommands.Step(uuid(), started.path("revision").asLong(), slots.get(1).id())));
        assertThat(resumed.status()).isEqualTo("WAITING_FOR_CONFIRMATION");
        var current = owner.client().getForEntity(workflowPath(started), JsonNode.class).getBody();
        assertThat(current.path("revision").asLong()).isEqualTo(resumed.revision());
        assertThat(step(owner.client(), current, "confirm").path("status").asText()).isEqualTo("COMPLETED");
    }

    @Autowired private WorkflowCheckpointRepository checkpointRepository;
    private WorkflowCheckpointRepository checkpoints() { return checkpointRepository; }
}
