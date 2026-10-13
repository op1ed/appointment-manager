package com.op1ed.appointmentmanager.agent;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.support.AgentApiFixtures;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeepSeekConversationIntegrationTest extends AgentApiFixtures {
    private static final ObjectMapper PROVIDER_JSON = new ObjectMapper();
    private static final Queue<String> REPLIES = new ConcurrentLinkedQueue<>();
    private static final Queue<JsonNode> REQUESTS = new ConcurrentLinkedQueue<>();
    private static HttpServer provider;
    private static ExecutorService executor;

    @DynamicPropertySource
    static void fakeProviderConfiguration(DynamicPropertyRegistry registry) throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        provider.setExecutor(executor);
        provider.createContext("/chat/completions", exchange -> {
            try {
                REQUESTS.add(PROVIDER_JSON.readTree(exchange.getRequestBody().readAllBytes()));
                String body = REPLIES.poll();
                byte[] data = (body == null ? "{}" : body).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(body == null ? 500 : 200, data.length);
                exchange.getResponseBody().write(data);
            } finally {
                exchange.close();
            }
        });
        provider.start();
        registry.add("app.agent.deepseek.enabled", () -> true);
        registry.add("app.agent.deepseek.api-key", () -> "integration-test-key");
        registry.add("app.agent.deepseek.endpoint", () -> "http://127.0.0.1:" + provider.getAddress().getPort() + "/chat/completions");
    }

    @AfterAll
    static void stopFakeProvider() {
        provider.stop(0);
        executor.shutdownNow();
    }

    @Test
    void shouldRunRealHttpAdapterToolLoopPersistenceAndSeparateBookingRescheduleCancellationConfirmation() throws Exception {
        REQUESTS.clear(); REPLIES.clear();
        var slots = createSlots(2);
        var owner = normalUser();
        var created = owner.client().postForEntity("/api/agent/conversations", null, JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String conversationPath = "/api/agent/conversations/" + created.getBody().path("id").asText();
        REPLIES.add(toolResponse("search_available_slots", Map.of("doctorId", slots.get(0).doctorId())));
        REPLIES.add(toolResponse("get_doctor", Map.of("doctorId", slots.get(0).doctorId())));
        REPLIES.add(toolResponse("prepare_booking", Map.of("customerName", "端到端预约人", "slotId", slots.get(0).id())));
        var firstCommand = Map.of("requestId", UUID.randomUUID().toString(), "message", "帮我预约这位医生的第一个可用时段");
        var booking = owner.client().postForEntity(conversationPath + "/messages", firstCommand, JsonNode.class);
        assertThat(booking.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(booking.getBody().path("status").asText()).isEqualTo("ACTION_PENDING");
        assertThat(booking.getBody().path("modelCalls").asInt()).isEqualTo(3);
        assertThat(REQUESTS).hasSize(3);
        assertThat(REQUESTS.element().path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(REQUESTS.element().path("tools")).hasSize(10);
        var rawRequests = List.copyOf(REQUESTS);
        assertThat(PROVIDER_JSON.readTree(rawRequests.get(1).path("messages").get(3).path("content").asText())
                .path("result").path("content").get(0).path("id").asLong()).isEqualTo(slots.get(0).id());
        assertThat(activeCount(slots.get(0).id())).isZero();
        assertThat(owner.client().postForEntity(conversationPath + "/messages", firstCommand, JsonNode.class).getBody()).isEqualTo(booking.getBody());
        assertThat(REQUESTS).hasSize(3);
        var booked = confirm(owner.client(), booking.getBody().path("actions").get(0));
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.OK);
        long appointmentId = booked.getBody().path("result").path("id").asLong();

        REPLIES.add(toolResponse("prepare_rescheduling", Map.of("appointmentId", appointmentId,
                "expectedSlotId", slots.get(0).id(), "targetSlotId", slots.get(1).id())));
        var move = owner.client().postForEntity(conversationPath + "/messages", Map.of("requestId", UUID.randomUUID().toString(),
                "message", "把刚才的预约改到第二个时段"), JsonNode.class);
        assertThat(move.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail(owner.client(), appointmentId).slotId()).isEqualTo(slots.get(0).id());
        assertThat(confirm(owner.client(), move.getBody().path("actions").get(0)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail(owner.client(), appointmentId).slotId()).isEqualTo(slots.get(1).id());

        REPLIES.add(toolResponse("prepare_cancellation", Map.of("appointmentId", appointmentId)));
        var cancel = owner.client().postForEntity(conversationPath + "/messages", Map.of("requestId", UUID.randomUUID().toString(),
                "message", "取消刚才的预约"), JsonNode.class);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeCount(slots.get(1).id())).isEqualTo(1L);
        assertThat(confirm(owner.client(), cancel.getBody().path("actions").get(0)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeCount(slots.get(1).id())).isZero();
        assertThat(history(owner.client(), appointmentId).totalElements()).isEqualTo(3L);
        var saved = owner.client().getForEntity(conversationPath, JsonNode.class).getBody();
        assertThat(saved.path("revision").asLong()).isEqualTo(3);
        assertThat(saved.path("turns")).hasSize(3);
        assertThat(REQUESTS).hasSize(5);
        assertThat(REPLIES).isEmpty();
    }

    @Test void shouldStartCustomGraphFromRealFrameworkToolAndStopBeforeSelection() throws Exception {
        REQUESTS.clear(); REPLIES.clear();
        var slots = createSlots(2); var owner = normalUser(); var appointment = book(owner.client(), slots.get(0).id());
        var conversation = owner.client().postForEntity("/api/agent/conversations", null, JsonNode.class).getBody();
        String path = "/api/agent/conversations/" + conversation.path("id").asText() + "/messages";
        REPLIES.add(toolResponse("start_rescheduling_workflow", Map.of("appointmentId", appointment.id(),
                "from", slots.get(0).startTime().toString(), "to", slots.get(1).endTime().toString())));
        var command = Map.of("requestId", UUID.randomUUID().toString(), "message", "把我的预约改到明天的另一个时段");
        var reply = owner.client().postForEntity(path, command, JsonNode.class);
        assertThat(reply.getStatusCode()).as("%s", reply.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(reply.getBody().path("status").asText()).isEqualTo("WORKFLOW_PENDING");
        assertThat(reply.getBody().path("workflows")).hasSize(1);
        var workflow = reply.getBody().path("workflows").get(0);
        assertThat(workflow.path("nextNode").asText()).isEqualTo("await_selection");
        assertThat(REQUESTS).hasSize(1);
        assertThat(owner.client().postForEntity(path, command, JsonNode.class).getBody()).isEqualTo(reply.getBody());
        assertThat(REQUESTS).hasSize(1);
        assertThat(detail(owner.client(), appointment.id()).slotId()).isEqualTo(slots.get(0).id());
        assertThat(owner.client().getForEntity("/api/agent/rescheduling-workflows/" + workflow.path("id").asText(), JsonNode.class).getBody()).isEqualTo(workflow);
    }

    private String toolResponse(String name, Object arguments) throws Exception {
        var root = PROVIDER_JSON.createObjectNode();
        var message = root.putArray("choices").addObject().put("finish_reason", "tool_calls")
                .putObject("message").put("role", "assistant").putNull("content");
        var call = message.putArray("tool_calls").addObject().put("id", "call_" + UUID.randomUUID()).put("type", "function");
        call.putObject("function").put("name", name).put("arguments", PROVIDER_JSON.writeValueAsString(arguments));
        return root.toString();
    }
}
