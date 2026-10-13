package com.op1ed.appointmentmanager.agent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.op1ed.appointmentmanager.doctor.web.dto.CreateDoctorRequest;
import com.op1ed.appointmentmanager.doctor.web.dto.DoctorResponse;
import com.op1ed.appointmentmanager.support.AgentApiFixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolsControllerTest extends AgentApiFixtures {
    @Test
    void shouldExposeNineFunctionDefinitionsWithoutConfirmationOrIdentityParameters() {
        var response = restTemplate.getForEntity("/api/agent/tools", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(10);
        for (JsonNode definition : response.getBody()) {
            assertThat(definition.path("type").asText()).isEqualTo("function");
            assertThat(definition.path("function").path("name").asText()).doesNotContain("confirm");
            JsonNode schema = definition.path("function").path("parameters");
            assertThat(schema.path("type").asText()).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            assertThat(schema.path("properties").has("ownerId")).isFalse();
            assertThat(schema.path("properties").has("userId")).isFalse();
            assertThat(schema.path("properties").has("administrator")).isFalse();
        }
    }

    @Test
    void shouldRequireLoginForToolsAndActionEndpoints() {
        var client = anonymousClient();
        assertAgentProblem(client.getForEntity("/api/agent/tools", JsonNode.class), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertAgentProblem(execute(client, "search_doctors", Map.of()), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        String path = "/api/agent/actions/" + UUID.randomUUID();
        assertAgentProblem(client.getForEntity(path, JsonNode.class), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertAgentProblem(client.postForEntity(path + "/confirm", null, JsonNode.class), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @Test
    void shouldSearchDoctorsByNameWithDatabasePaginationAndEscapedWildcards() {
        String name = "工具医生_%_" + UUID.randomUUID();
        for (int index = 0; index < 3; index++) {
            assertThat(restTemplate.postForEntity("/api/doctors", new CreateDoctorRequest(name + index), DoctorResponse.class)
                    .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        JsonNode first = tool(restTemplate, "search_doctors", Map.of("name", name, "page", 0, "size", 2));
        JsonNode next = tool(restTemplate, "search_doctors", Map.of("name", name, "page", 1, "size", 2));
        assertThat(first.path("totalElements").asLong()).isEqualTo(3);
        assertThat(first.path("content")).hasSize(2);
        assertThat(first.path("hasNext").asBoolean()).isTrue();
        assertThat(next.path("content")).hasSize(1);
        assertThat(next.path("hasNext").asBoolean()).isFalse();
        JsonNode doctor = tool(restTemplate, "get_doctor", Map.of("doctorId", next.path("content").get(0).path("id").asLong()));
        assertThat(doctor).isEqualTo(next.path("content").get(0));
    }

    @Test
    void shouldSearchOnlyFutureFreeSlotsWithinDoctorAndHalfOpenTimeRange() {
        var slots = createSlots(5);
        createSlots(1); // 另一医生的时段不能出现在结果中。
        book(restTemplate, slots.get(1).id());
        db.update("UPDATE appointment_slots SET start_time = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), "
                + "end_time = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 MINUTE) WHERE id = ?", slots.get(0).id());
        JsonNode first = tool(restTemplate, "search_available_slots", Map.of("doctorId", slots.get(0).doctorId(), "size", 1));
        assertThat(first.path("totalElements").asLong()).isEqualTo(3);
        assertThat(first.path("content").get(0).path("id").asLong()).isEqualTo(slots.get(2).id());
        assertThat(first.path("hasNext").asBoolean()).isTrue();
        JsonNode filtered = tool(restTemplate, "search_available_slots", Map.of("doctorId", slots.get(0).doctorId(),
                "from", slots.get(3).startTime().toString(), "to", slots.get(4).startTime().toString()));
        assertThat(filtered.path("totalElements").asLong()).isEqualTo(1);
        assertThat(filtered.path("content").get(0).path("id").asLong()).isEqualTo(slots.get(3).id());
        assertThat(filtered.path("content").get(0).path("available").asBoolean()).isTrue();
    }

    @Test
    void shouldReturnCancelledSlotToAvailableSearch() {
        var slot = createSlots(1).get(0);
        var appointment = book(restTemplate, slot.id());
        assertThat(tool(restTemplate, "search_available_slots", Map.of("doctorId", slot.doctorId())).path("totalElements").asLong())
                .isZero();
        restTemplate.postForEntity(path(appointment.id()) + "/cancel", null, String.class);
        assertThat(tool(restTemplate, "search_available_slots", Map.of("doctorId", slot.doctorId())).path("totalElements").asLong())
                .isEqualTo(1);
    }

    @Test
    void shouldApplyOwnershipToDetailsSearchAndHistoryThroughTools() {
        var slots = createSlots(2);
        var owner = normalUser();
        var other = normalUser();
        var owned = book(owner.client(), slots.get(0).id());
        var foreign = book(other.client(), slots.get(1).id());
        JsonNode own = tool(owner.client(), "search_appointments", Map.of());
        assertThat(own.path("totalElements").asLong()).isEqualTo(1);
        assertThat(own.path("content").get(0).path("id").asLong()).isEqualTo(owned.id());
        assertThat(tool(owner.client(), "get_appointment", Map.of("appointmentId", owned.id())).path("id").asLong())
                .isEqualTo(owned.id());
        assertThat(tool(owner.client(), "get_appointment_history", Map.of("appointmentId", owned.id())).path("totalElements").asLong())
                .isEqualTo(1);
        for (String tool : List.of("get_appointment", "get_appointment_history", "prepare_cancellation")) {
            assertAgentProblem(execute(owner.client(), tool, Map.of("appointmentId", foreign.id())), HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        assertAgentProblem(execute(owner.client(), "prepare_rescheduling", Map.of("appointmentId", foreign.id(),
                "expectedSlotId", foreign.slotId(), "targetSlotId", owned.slotId())), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(tool(restTemplate, "get_appointment", Map.of("appointmentId", foreign.id())).path("id").asLong())
                .isEqualTo(foreign.id());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"slotId\":1}", "{\"slotId\":1,\"customerName\":null}",
            "{\"slotId\":\"1\",\"customerName\":\"甲\"}", "{\"slotId\":1.2,\"customerName\":\"甲\"}",
            "{\"slotId\":9223372036854775808,\"customerName\":\"甲\"}",
            "{\"slotId\":0,\"customerName\":\"甲\"}", "{\"slotId\":1,\"customerName\":\"  \"}",
            "{\"slotId\":1,\"customerName\":\"甲\",\"ownerId\":1}",
            "{\"slotId\":1,\"customerName\":\"甲\",\"administrator\":true}", "[]"
    })
    void shouldRejectMalformedOrInjectedArgumentsBeforeCreatingActions(String arguments) throws Exception {
        long count = db.queryForObject("SELECT COUNT(*) FROM agent_actions", Long.class);
        assertAgentProblem(execute(restTemplate, "prepare_booking", json.readTree(arguments)), HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM agent_actions", Long.class)).isEqualTo(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"page\":-1}", "{\"size\":101}", "{\"page\":2147483647,\"size\":100}",
            "{\"page\":null}", "{\"size\":true}", "{\"status\":\"ADMIN\"}",
            "{\"from\":\"2026-10-13T09:00:00\"}", "{\"from\":\"2026-10-13T10:00:00Z\",\"to\":\"2026-10-13T09:00:00Z\"}"})
    void shouldRejectInvalidQueryArguments(String arguments) throws Exception {
        assertAgentProblem(execute(restTemplate, "search_appointments", json.readTree(arguments)), HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
    }

    @Test
    void shouldRejectUnknownToolsIncludingConfirmation() {
        for (String name : List.of("execute_sql", "confirm_action", "delete_appointment")) {
            assertAgentProblem(execute(restTemplate, name, Map.of()), HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        }
    }
}
