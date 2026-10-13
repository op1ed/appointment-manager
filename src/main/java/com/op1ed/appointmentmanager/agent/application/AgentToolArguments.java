package com.op1ed.appointmentmanager.agent.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

// 使用目录中的同一份 schema 校验参数，避免工具定义和实际执行规则分离。
public final class AgentToolArguments {
    private final JsonNode arguments;

    public AgentToolArguments(JsonNode arguments, AgentToolDefinition definition) {
        if (arguments == null || !arguments.isObject()) {
            throw invalid("工具参数必须是 JSON 对象");
        }
        JsonNode schema = definition.function().parameters();
        for (JsonNode required : schema.path("required")) {
            if (!arguments.has(required.asText())) {
                throw invalid("缺少参数：" + required.asText());
            }
        }
        arguments.fields().forEachRemaining(field -> {
            JsonNode fieldSchema = schema.path("properties").get(field.getKey());
            if (fieldSchema == null) {
                throw invalid("工具不接受参数：" + field.getKey());
            }
            validate(field.getKey(), field.getValue(), fieldSchema);
        });
        this.arguments = arguments;
    }

    public long id(String name) {
        return arguments.path(name).longValue();
    }

    public int page() {
        return arguments.has("page") ? arguments.get("page").intValue() : 0;
    }

    public int size() {
        return arguments.has("size") ? arguments.get("size").intValue() : 20;
    }

    public String text(String name) {
        JsonNode value = arguments.get(name);
        return value == null || value.isNull() ? null : value.textValue();
    }

    public Instant time(String name) {
        String value = text(name);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException exception) {
            throw invalid(name + " 必须为带时区的 ISO 8601 时间");
        }
    }

    private static void validate(String name, JsonNode value, JsonNode schema) {
        JsonNode type = schema.path("type");
        boolean nullable = type.isArray() && contains(type, "null");
        if (value.isNull()) {
            if (!nullable) {
                throw invalid(name + " 不能为 null");
            }
            return;
        }
        boolean integer = type.isTextual() && type.asText().equals("integer");
        if (integer) {
            if (!value.isIntegralNumber() || !value.canConvertToLong()
                    || value.longValue() < schema.path("minimum").longValue()
                    || value.longValue() > schema.path("maximum").longValue()) {
                throw invalid(name + " 必须为允许范围内的整数");
            }
        } else {
            if (!value.isTextual() || value.textValue().length() > schema.path("maxLength").intValue()
                    || (schema.has("minLength") && value.textValue().length() < schema.path("minLength").intValue())) {
                throw invalid(name + " 必须为允许长度的字符串");
            }
        }
        if (schema.has("enum") && !contains(schema.get("enum"), value.asText())) {
            throw invalid(name + " 不在允许值范围内");
        }
    }

    private static boolean contains(JsonNode values, String target) {
        for (JsonNode value : values) {
            if (value.isTextual() && value.asText().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
