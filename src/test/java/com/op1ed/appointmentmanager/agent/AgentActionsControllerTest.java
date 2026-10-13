package com.op1ed.appointmentmanager.agent;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.op1ed.appointmentmanager.support.AgentApiFixtures;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AgentActionsControllerTest extends AgentApiFixtures {
    @Test
    void shouldPrepareWithoutBookingAndConfirmStoredServerValidatedParameters() {
        var slots = createSlots(2);
        var owner = normalUser();
        JsonNode action = tool(owner.client(), "prepare_booking", Map.of("customerName", "  张三  ", "slotId", slots.get(0).id()));
        assertThat(action.path("status").asText()).isEqualTo("PENDING");
        assertThat(action.path("kind").asText()).isEqualTo("BOOK");
        assertThat(action.path("preview").path("after").path("customerName").asText()).isEqualTo("张三");
        assertThat(action.path("preview").path("after").path("slotId").asLong()).isEqualTo(slots.get(0).id());
        assertThat(action.path("preview").path("after").path("doctorName").asText()).isNotBlank();
        assertThat(activeCount(slots.get(0).id())).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_events WHERE to_slot_id = ?", Long.class, slots.get(0).id())).isZero();
        assertThat(owner.client().getForEntity(actionPath(action), JsonNode.class).getBody()).isEqualTo(action);

        // 确认接口不接受重新指定操作参数；只使用动作中保存的内容。
        var confirmed = owner.client().postForEntity(actionPath(action) + "/confirm", Map.of("slotId", slots.get(1).id(),
                "customerName", "被替换", "ownerId", 1, "administrator", true), JsonNode.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode result = confirmed.getBody().path("result");
        assertThat(confirmed.getBody().path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(result.path("customerName").asText()).isEqualTo("张三");
        assertThat(result.path("slotId").asLong()).isEqualTo(slots.get(0).id());
        assertThat(activeCount(slots.get(1).id())).isZero();
        assertThat(db.queryForObject("SELECT owner_id FROM appointments WHERE id = ?", Long.class, result.path("id").asLong()))
                .isEqualTo(owner.identity().id());
        assertThat(history(owner.client(), result.path("id").asLong()).content().get(0).actorId()).isEqualTo(owner.identity().id());
    }

    @Test
    void shouldAllowOnlyActionOwnerToReadAndConfirmEvenForAdministrator() {
        var owner = normalUser();
        var other = normalUser();
        JsonNode action = prepareBooking(owner.client(), createSlots(1).get(0).id());
        for (var client : java.util.List.of(other.client(), restTemplate)) {
            assertAgentProblem(client.getForEntity(actionPath(action), JsonNode.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
            assertAgentProblem(confirm(client, action), HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        assertThat(confirm(owner.client(), action).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldExecuteConcurrentConfirmationsOnceAndReturnIdenticalResults() throws Exception {
        var slot = createSlots(1).get(0);
        JsonNode action = prepareBooking(restTemplate, slot.id());
        var responses = sendTogether(
                () -> restTemplate.postForEntity(actionPath(action) + "/confirm", null, String.class),
                () -> restTemplate.postForEntity(actionPath(action) + "/confirm", null, String.class));
        assertThat(responses).allSatisfy(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK));
        assertThat(json.readTree(responses.get(0).getBody())).isEqualTo(json.readTree(responses.get(1).getBody()));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Long.class, slot.id())).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_events WHERE to_slot_id = ?", Long.class, slot.id())).isEqualTo(1L);
    }

    @Test
    void shouldReturnOriginalResultOnRetryAfterAppointmentChangesAndActionExpiry() {
        JsonNode action = prepareBooking(restTemplate, createSlots(1).get(0).id());
        var original = confirm(restTemplate, action).getBody().path("result");
        assertThat(restTemplate.postForEntity(path(original.path("id").asLong()) + "/cancel", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        expire(action);
        var replay = confirm(restTemplate, action);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().path("result")).isEqualTo(original);
        assertThat(replay.getBody().path("result").path("status").asText()).isEqualTo("BOOKED");
        assertThat(history(restTemplate, original.path("id").asLong()).totalElements()).isEqualTo(2L);
    }

    @Test
    void shouldRejectExpiredPendingActionWithoutBusinessChanges() {
        var slot = createSlots(1).get(0);
        JsonNode action = prepareBooking(restTemplate, slot.id());
        expire(action);
        assertThat(restTemplate.getForEntity(actionPath(action), JsonNode.class).getBody().path("status").asText()).isEqualTo("EXPIRED");
        assertAgentProblem(confirm(restTemplate, action), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(activeCount(slot.id())).isZero();
        assertPending(action);
    }

    @Test
    void shouldRejectBadActionIdAndReturnNotFoundForUnknownCanonicalId() {
        assertAgentProblem(restTemplate.postForEntity("/api/agent/actions/invalid/confirm", null, JsonNode.class),
                HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        assertAgentProblem(restTemplate.getForEntity("/api/agent/actions/1-1-1-1-1", JsonNode.class),
                HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        assertAgentProblem(restTemplate.postForEntity("/api/agent/actions/" + UUID.randomUUID() + "/confirm", null, JsonNode.class),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    @Test
    void shouldRecheckOccupancyAtBookingConfirmationAndLeaveActionPendingOnFailure() {
        var slot = createSlots(1).get(0);
        var user = normalUser();
        JsonNode action = prepareBooking(user.client(), slot.id());
        var winner = book(restTemplate, slot.id());
        assertAgentProblem(confirm(user.client(), action), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(detail(restTemplate, winner.id())).isEqualTo(winner);
        assertThat(activeCount(slot.id())).isEqualTo(1L);
        assertPending(action);
    }

    @Test
    void shouldRejectTwoDifferentPreparedBookingsCompetingForOneSlot() throws Exception {
        var slot = createSlots(1).get(0);
        JsonNode left = prepareBooking(restTemplate, slot.id());
        JsonNode right = prepareBooking(restTemplate, slot.id());
        var responses = sendTogether(
                () -> restTemplate.postForEntity(actionPath(left) + "/confirm", null, String.class),
                () -> restTemplate.postForEntity(actionPath(right) + "/confirm", null, String.class));
        assertThat(responses.stream().map(response -> response.getStatusCode().value())).containsExactlyInAnyOrder(200, 409);
        assertThat(activeCount(slot.id())).isEqualTo(1L);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM agent_actions WHERE id IN (?, ?) AND status = 'CONFIRMED'",
                Long.class, left.path("id").asText(), right.path("id").asText())).isEqualTo(1L);
    }

    @Test
    void shouldPrepareAndConfirmCancellationWithoutDuplicateEvents() {
        var owner = normalUser();
        var appointment = book(owner.client(), createSlots(1).get(0).id());
        JsonNode action = tool(owner.client(), "prepare_cancellation", Map.of("appointmentId", appointment.id()));
        assertThat(detail(owner.client(), appointment.id())).isEqualTo(appointment);
        assertThat(action.path("preview").path("before").path("id").asLong()).isEqualTo(appointment.id());
        var first = confirm(owner.client(), action);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().path("result").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(confirm(owner.client(), action).getBody()).isEqualTo(first.getBody());
        assertThat(activeCount(appointment.slotId())).isZero();
        assertThat(history(owner.client(), appointment.id()).totalElements()).isEqualTo(2L);
    }

    @Test
    void shouldRejectCancellationIfAppointmentWasMovedAfterPreview() {
        var slots = createSlots(2);
        var original = book(restTemplate, slots.get(0).id());
        JsonNode action = tool(restTemplate, "prepare_cancellation", Map.of("appointmentId", original.id()));
        assertThat(reschedule(restTemplate, original, slots.get(1).id()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertAgentProblem(confirm(restTemplate, action), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(detail(restTemplate, original.id()).slotId()).isEqualTo(slots.get(1).id());
        assertThat(activeCount(slots.get(1).id())).isEqualTo(1L);
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(2L);
        assertPending(action);
    }

    @Test
    void shouldTreatCancellationAlreadyPerformedElsewhereAsSuccessfulNoOp() {
        var original = book(restTemplate, createSlots(1).get(0).id());
        JsonNode action = tool(restTemplate, "prepare_cancellation", Map.of("appointmentId", original.id()));
        restTemplate.postForEntity(path(original.id()) + "/cancel", null, String.class);
        var confirmed = confirm(restTemplate, action);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().path("result").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(2L);
    }

    @Test
    void shouldPrepareAndConfirmReschedulingAndKeepIdentityAndHistory() {
        var slots = createSlots(2);
        var owner = normalUser();
        var original = book(owner.client(), slots.get(0).id());
        JsonNode action = tool(owner.client(), "prepare_rescheduling", Map.of("appointmentId", original.id(),
                "expectedSlotId", original.slotId(), "targetSlotId", slots.get(1).id()));
        assertThat(detail(owner.client(), original.id())).isEqualTo(original);
        assertThat(action.path("preview").path("before").path("slotId").asLong()).isEqualTo(original.slotId());
        assertThat(action.path("preview").path("after").path("slotId").asLong()).isEqualTo(slots.get(1).id());
        var confirmed = confirm(owner.client(), action);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().path("result").path("id").asLong()).isEqualTo(original.id());
        assertThat(confirmed.getBody().path("result").path("slotId").asLong()).isEqualTo(slots.get(1).id());
        assertThat(detail(owner.client(), original.id()).customerName()).isEqualTo(original.customerName());
        assertThat(db.queryForObject("SELECT owner_id FROM appointments WHERE id = ?", Long.class, original.id())).isEqualTo(owner.identity().id());
        assertThat(history(owner.client(), original.id()).content().get(1).actorId()).isEqualTo(owner.identity().id());
        assertThat(confirm(owner.client(), action).getBody()).isEqualTo(confirmed.getBody());
        assertThat(history(owner.client(), original.id()).totalElements()).isEqualTo(2L);
    }

    @Test
    void shouldRejectReschedulingIfTargetWasOccupiedAfterPreview() {
        var slots = createSlots(2);
        var original = book(restTemplate, slots.get(0).id());
        JsonNode action = tool(restTemplate, "prepare_rescheduling", Map.of("appointmentId", original.id(),
                "expectedSlotId", original.slotId(), "targetSlotId", slots.get(1).id()));
        book(restTemplate, slots.get(1).id());
        assertAgentProblem(confirm(restTemplate, action), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(detail(restTemplate, original.id())).isEqualTo(original);
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(1L);
        assertPending(action);
    }

    @Test
    void shouldRejectReschedulingIfSourceWasMovedAfterPreview() {
        var slots = createSlots(3);
        var original = book(restTemplate, slots.get(0).id());
        JsonNode action = tool(restTemplate, "prepare_rescheduling", Map.of("appointmentId", original.id(),
                "expectedSlotId", original.slotId(), "targetSlotId", slots.get(1).id()));
        assertThat(reschedule(restTemplate, original, slots.get(2).id()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertAgentProblem(confirm(restTemplate, action), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(detail(restTemplate, original.id()).slotId()).isEqualTo(slots.get(2).id());
        assertThat(activeCount(slots.get(1).id())).isZero();
        assertPending(action);
    }

    @Test
    void shouldRejectUnavailableOrUnknownSlotAtPreparation() {
        var slot = createSlots(1).get(0);
        book(restTemplate, slot.id());
        assertAgentProblem(execute(restTemplate, "prepare_booking", Map.of("customerName", "甲", "slotId", slot.id())),
                HttpStatus.CONFLICT, "CONFLICT");
        assertAgentProblem(execute(restTemplate, "prepare_booking", Map.of("customerName", "甲", "slotId", Long.MAX_VALUE)),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    private void expire(JsonNode action) {
        db.update("UPDATE agent_actions SET created_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 10 MINUTE), "
                + "expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 5 MINUTE) WHERE id = ?", action.path("id").asText());
    }

    private void assertPending(JsonNode action) {
        var row = db.queryForMap("SELECT status, result_json, confirmed_at FROM agent_actions WHERE id = ?", action.path("id").asText());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("result_json")).isNull();
        assertThat(row.get("confirmed_at")).isNull();
    }
}
