package com.op1ed.appointmentmanager.agent.infrastructure.deepseek;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.op1ed.appointmentmanager.agent.application.AgentMessage;
import com.op1ed.appointmentmanager.agent.application.AgentModel;
import com.op1ed.appointmentmanager.agent.application.AgentModelReply;
import com.op1ed.appointmentmanager.agent.application.AgentModelToolCall;
import com.op1ed.appointmentmanager.agent.application.AgentToolDefinition;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.stereotype.Component;

@Component
public class DeepSeekAgentModel implements AgentModel {
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private final DeepSeekProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    public DeepSeekAgentModel(DeepSeekProperties properties, ObjectMapper json) {
        validateConfiguration(properties);
        this.properties = properties;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public boolean available() {
        String key = properties.getApiKey();
        return properties.isEnabled() && key != null && key.matches("[A-Za-z0-9._~+/-]{1,512}=*");
    }

    @Override
    public AgentModelReply complete(List<AgentMessage> messages, List<AgentToolDefinition> tools, Duration timeout) {
        if (!available()) {
            throw new BusinessException(ErrorCode.AGENT_UNAVAILABLE, "对话服务尚未启用，请先配置 DeepSeek");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new BusinessException(ErrorCode.MODEL_TIMEOUT, "模型请求已超时");
        }
        Duration requestTimeout = timeout.compareTo(properties.getRequestTimeout()) < 0 ? timeout : properties.getRequestTimeout();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            ObjectNode body = json.createObjectNode().put("model", properties.getModel()).put("stream", false)
                    .put("max_tokens", properties.getMaxTokens()).put("tool_choice", "auto");
            // 当前使用非思考模式，不接收或保存模型的 reasoning_content。
            body.putObject("thinking").put("type", "disabled");
            body.set("tools", json.valueToTree(tools));
            var serializedMessages = body.putArray("messages");
            for (AgentMessage message : messages) {
                ObjectNode serialized = serializedMessages.addObject().put("role", message.role());
                if (message.content() == null) {
                    serialized.putNull("content");
                } else {
                    serialized.put("content", message.content());
                }
                if (!message.toolCalls().isEmpty()) {
                    var calls = serialized.putArray("tool_calls");
                    for (AgentModelToolCall call : message.toolCalls()) {
                        var item = calls.addObject().put("id", call.id()).put("type", "function");
                        item.putObject("function").put("name", call.name()).put("arguments", call.arguments());
                    }
                }
                if (message.toolCallId() != null) {
                    serialized.put("tool_call_id", message.toolCallId());
                }
            }
            HttpRequest request = HttpRequest.newBuilder(properties.getEndpoint()).timeout(requestTimeout)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            pending = http.sendAsync(request, info -> new LimitedResponseSubscriber(MAX_RESPONSE_BYTES));
            // 外层等待也设超时，覆盖收到响应头之后缓慢接收正文的情况。
            var response = pending.get(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() != 200) {
                throw modelError();
            }
            return parse(json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(response.body()));
        } catch (TimeoutException exception) {
            if (pending != null) {
                pending.cancel(true);
            }
            throw new BusinessException(ErrorCode.MODEL_TIMEOUT, "DeepSeek 请求超时，请稍后重试");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof HttpTimeoutException) {
                throw new BusinessException(ErrorCode.MODEL_TIMEOUT, "DeepSeek 请求超时，请稍后重试");
            }
            throw modelError();
        } catch (InterruptedException exception) {
            if (pending != null) {
                pending.cancel(true);
            }
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MODEL_TIMEOUT, "模型请求已中断");
        } catch (IOException | IllegalArgumentException exception) {
            // 不转发上游错误正文、异常消息、请求头或 API Key。
            throw modelError();
        }
    }

    private AgentModelReply parse(JsonNode response) {
        JsonNode choices = response == null ? null : response.get("choices");
        if (choices == null || !choices.isArray() || choices.size() != 1) {
            throw modelError();
        }
        JsonNode choice = choices.get(0);
        JsonNode message = choice.path("message");
        String reason = choice.path("finish_reason").asText();
        if (!message.isObject() || !message.path("role").asText().equals("assistant")
                || (!reason.equals("stop") && !reason.equals("tool_calls"))) {
            throw modelError();
        }
        JsonNode content = message.get("content");
        if (content != null && !content.isNull() && (!content.isTextual() || content.textValue().length() > 20000)) {
            throw modelError();
        }
        List<AgentModelToolCall> calls = new ArrayList<>();
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls != null && !toolCalls.isNull()) {
            if (!toolCalls.isArray() || toolCalls.size() > 20) {
                throw modelError();
            }
            var ids = new HashSet<String>();
            for (JsonNode call : toolCalls) {
                JsonNode function = call.path("function");
                String id = requireText(call.get("id"), 128);
                if (!call.path("type").asText().equals("function") || !ids.add(id)) {
                    throw modelError();
                }
                calls.add(new AgentModelToolCall(id, requireText(function.get("name"), 80),
                        requireText(function.get("arguments"), 8000)));
            }
        }
        if (reason.equals("tool_calls") != !calls.isEmpty()) {
            throw modelError();
        }
        String text = content == null || content.isNull() ? "" : content.textValue();
        if (calls.isEmpty() && text.isBlank()) {
            throw modelError();
        }
        return new AgentModelReply(text, calls);
    }

    private String requireText(JsonNode value, int maximumLength) {
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > maximumLength) {
            throw modelError();
        }
        return value.textValue();
    }

    private static BusinessException modelError() {
        return new BusinessException(ErrorCode.MODEL_ERROR, "DeepSeek 暂时无法完成请求，请检查模型服务配置或稍后重试");
    }

    private static void validateConfiguration(DeepSeekProperties properties) {
        URI endpoint = properties.getEndpoint();
        String host = endpoint == null ? null : endpoint.getHost();
        boolean official = "api.deepseek.com".equals(host) && "https".equals(endpoint.getScheme());
        boolean loopback = ("127.0.0.1".equals(host) || "[::1]".equals(host)) && "http".equals(endpoint.getScheme());
        if ((!official && !loopback) || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                || properties.getModel() == null || !properties.getModel().matches("[A-Za-z0-9._-]{1,80}")
                || properties.getConnectTimeout() == null || properties.getConnectTimeout().isNegative() || properties.getConnectTimeout().isZero()
                || properties.getRequestTimeout() == null || properties.getRequestTimeout().isNegative() || properties.getRequestTimeout().isZero()
                || properties.getRequestTimeout().compareTo(Duration.ofSeconds(60)) > 0
                || properties.getMaxTokens() < 1 || properties.getMaxTokens() > 4096) {
            throw new IllegalArgumentException("DeepSeek 连接配置不合法");
        }
    }
}
