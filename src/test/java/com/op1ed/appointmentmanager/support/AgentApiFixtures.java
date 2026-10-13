package com.op1ed.appointmentmanager.support;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AgentApiFixtures extends AppointmentApiFixtures {
    @Autowired protected ObjectMapper json;

    protected ResponseEntity<JsonNode> execute(TestRestTemplate client, String name, Object arguments) {
        return client.postForEntity("/api/agent/tools/execute", Map.of("name", name, "arguments", arguments), JsonNode.class);
    }

    protected JsonNode tool(TestRestTemplate client, String name, Object arguments) {
        var response = execute(client, name, arguments);
        assertThat(response.getStatusCode()).as("%s: %s", name, response.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("name").asText()).isEqualTo(name);
        return response.getBody().path("result");
    }

    protected JsonNode prepareBooking(TestRestTemplate client, long slotId) {
        return tool(client, "prepare_booking", Map.of("customerName", "Agent 预约人", "slotId", slotId));
    }

    protected ResponseEntity<JsonNode> confirm(TestRestTemplate client, JsonNode action) {
        return client.postForEntity(actionPath(action) + "/confirm", null, JsonNode.class);
    }

    protected String actionPath(JsonNode action) {
        return "/api/agent/actions/" + action.path("id").asText();
    }

    protected void assertAgentProblem(ResponseEntity<JsonNode> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("code").asText()).isEqualTo(code);
        assertThat(response.getBody().path("status").asInt()).isEqualTo(status.value());
    }
}
