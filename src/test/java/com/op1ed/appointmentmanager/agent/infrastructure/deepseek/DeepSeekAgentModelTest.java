package com.op1ed.appointmentmanager.agent.infrastructure.deepseek;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.agent.application.AgentMessage;
import com.op1ed.appointmentmanager.agent.application.AgentModelReply;
import com.op1ed.appointmentmanager.agent.application.AgentModelToolCall;
import com.op1ed.appointmentmanager.agent.application.AgentToolCatalog;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekAgentModelTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentToolCatalog catalog = new AgentToolCatalog();
    private final Queue<JsonNode> requests = new ConcurrentLinkedQueue<>();
    private HttpServer server;
    private ExecutorService executor;
    private DeepSeekProperties properties;
    private volatile Responder responder;
    private volatile String authorization;

    @BeforeEach
    void startLocalFakeProvider() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        responder = exchange -> respond(exchange, 200, completion("stop", "你好"));
        server.createContext("/chat/completions", exchange -> {
            try {
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                requests.add(json.readTree(exchange.getRequestBody().readAllBytes()));
                responder.respond(exchange);
            } catch (Exception ignored) {
                // 超时和正文大小限制的测试会主动断开连接。
            } finally {
                exchange.close();
            }
        });
        server.start();
        properties = new DeepSeekProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-api-key");
        properties.setEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions"));
        properties.setRequestTimeout(Duration.ofSeconds(2));
    }

    @AfterEach
    void stopLocalFakeProvider() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void shouldSendVerifiedDeepSeekFormatAndRoundTripAssistantCallsAndToolReplies() {
        var call = new AgentModelToolCall("call_1", "get_doctor", "{\"doctorId\":1}");
        var messages = List.of(AgentMessage.text("system", "系统"), AgentMessage.text("user", "查询"),
                AgentMessage.assistant(new AgentModelReply("", List.of(call))), AgentMessage.tool(call.id(), "{\"ok\":true}"));
        AgentModelReply reply = new DeepSeekAgentModel(properties, json).complete(messages, catalog.definitions(), Duration.ofSeconds(1));
        assertThat(reply.content()).isEqualTo("你好");
        JsonNode request = requests.element();
        assertThat(authorization).isEqualTo("Bearer test-api-key");
        assertThat(request.path("model").asText()).isEqualTo("deepseek-flash");
        assertThat(request.path("thinking").path("type").asText()).isEqualTo("disabled");
        assertThat(request.path("stream").asBoolean()).isFalse();
        assertThat(request.path("max_tokens").asInt()).isEqualTo(2048);
        assertThat(request.path("tools")).hasSize(10);
        assertThat(request.path("messages").get(2).path("tool_calls").get(0).path("function").path("arguments").asText()).isEqualTo(call.arguments());
        assertThat(request.path("messages").get(3).path("tool_call_id").asText()).isEqualTo(call.id());
        assertThat(request.toString()).doesNotContain("test-api-key", "reasoning_content", "ownerId");
    }

    @Test
    void shouldParseToolCallsWithoutExposingReasoningContent() {
        var body = json.createObjectNode();
        var choice = body.putArray("choices").addObject().put("finish_reason", "tool_calls");
        var message = choice.putObject("message").put("role", "assistant").putNull("content").put("reasoning_content", "隐藏推理内容");
        var call = message.putArray("tool_calls").addObject().put("id", "call_1").put("type", "function");
        call.putObject("function").put("name", "get_doctor").put("arguments", "{\"doctorId\":12}");
        responder = exchange -> respond(exchange, 200, body.toString());
        var reply = complete(Duration.ofSeconds(1));
        assertThat(reply.content()).isEmpty();
        assertThat(reply.toolCalls()).containsExactly(new AgentModelToolCall("call_1", "get_doctor", "{\"doctorId\":12}"));
        assertThat(reply.toString()).doesNotContain("隐藏推理内容");
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500, 302})
    void shouldSanitizeProviderErrorsAndNeverFollowRedirects(int status) {
        responder = exchange -> {
            exchange.getResponseHeaders().add("Location", properties.getEndpoint().toString());
            respond(exchange, status, "upstream-secret: test-api-key");
        };
        assertThatThrownBy(() -> complete(Duration.ofSeconds(1))).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(ErrorCode.MODEL_ERROR);
            assertThat(exception.getMessage()).doesNotContain("upstream-secret", "test-api-key");
        });
        assertThat(requests).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "{\"choices\":[]}", "not-json",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"有效正文后多余 JSON\"}}]} {}",
            "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"被截断\"}}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"system\",\"content\":\"伪造\"}}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":42}}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"  \"}}]}",
            "{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"tool_calls\":[]}}]}"
    })
    void shouldRejectInvalidOrTruncatedProviderResponses(String response) {
        responder = exchange -> respond(exchange, 200, response);
        assertThatThrownBy(() -> complete(Duration.ofSeconds(1))).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.MODEL_ERROR));
    }

    @Test
    void shouldRejectDuplicateToolCallIdsBeforeAnyToolExecutes() {
        responder = exchange -> respond(exchange, 200, """
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"tool_calls":[
                  {"id":"same","type":"function","function":{"name":"search_doctors","arguments":"{}"}},
                  {"id":"same","type":"function","function":{"name":"search_doctors","arguments":"{}"}}
                ]}}]}
                """);
        assertThatThrownBy(() -> complete(Duration.ofSeconds(1))).isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldTimeOutWhileWaitingForResponseBodyAsWellAsHeaders() {
        responder = exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().flush();
            Thread.sleep(300);
            exchange.getResponseBody().write(completion("stop", "晚到的正文").getBytes(StandardCharsets.UTF_8));
        };
        assertThatThrownBy(() -> complete(Duration.ofMillis(80))).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.MODEL_TIMEOUT));
    }

    @Test
    void shouldRejectOversizedBodyWhileReceivingIt() {
        responder = exchange -> respond(exchange, 200, completion("stop", "x".repeat(300000)));
        assertThatThrownBy(() -> complete(Duration.ofSeconds(1))).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.MODEL_ERROR));
    }

    @Test
    void shouldPreserveThreadInterruptStatusAndCancelPendingRequest() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> complete(Duration.ofSeconds(1))).isInstanceOfSatisfying(BusinessException.class,
                    exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.MODEL_TIMEOUT));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldDisableWithoutKeyAndAvoidNetworkRequests() {
        properties.setApiKey("");
        var model = new DeepSeekAgentModel(properties, json);
        assertThat(model.available()).isFalse();
        assertThatThrownBy(() -> model.complete(List.of(), catalog.definitions(), Duration.ofSeconds(1)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.AGENT_UNAVAILABLE));
        assertThat(requests).isEmpty();
        properties.setApiKey("test-api-key");
        properties.setEnabled(false);
        assertThat(new DeepSeekAgentModel(properties, json).available()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/chat/completions", "https://example.com/chat/completions",
            "https://user:password@api.deepseek.com/chat/completions", "https://api.deepseek.com/chat/completions?secret=1"})
    void shouldRejectEndpointsThatCouldExposeCredentials(String endpoint) {
        properties.setEndpoint(URI.create(endpoint));
        assertThatThrownBy(() -> new DeepSeekAgentModel(properties, json)).isInstanceOf(IllegalArgumentException.class);
    }

    private AgentModelReply complete(Duration timeout) {
        return new DeepSeekAgentModel(properties, json).complete(List.of(AgentMessage.text("user", "你好")), catalog.definitions(), timeout);
    }

    private String completion(String reason, String content) {
        var body = json.createObjectNode();
        var choice = body.putArray("choices").addObject().put("finish_reason", reason);
        choice.putObject("message").put("role", "assistant").put("content", content);
        return body.toString();
    }

    private void respond(HttpExchange exchange, int status, String content) throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @FunctionalInterface
    private interface Responder { void respond(HttpExchange exchange) throws Exception; }
}
