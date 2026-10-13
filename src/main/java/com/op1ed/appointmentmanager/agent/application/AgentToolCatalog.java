package com.op1ed.appointmentmanager.agent.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.stereotype.Component;

@Component
public class AgentToolCatalog {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private final List<AgentToolDefinition> definitions;

    public AgentToolCatalog() {
        definitions = List.of(
                tool("search_doctors", "按姓名关键词查询医生，返回分页结果。页号从 0 开始，每页最多 100 条。",
                        fields("page", page(), "size", size(), "name", text("医生姓名关键词，null 表示不筛选", 100, true))),
                tool("get_doctor", "按医生 ID 查询医生信息。", fields("doctorId", id("医生 ID")), "doctorId"),
                tool("search_available_slots", "查询指定医生未来且未被预约的时段。结果是当前快照，预约确认时仍需检查占用。",
                        fields("doctorId", id("医生 ID"), "page", page(), "size", size(),
                                "from", time("开始时间下界，包含该时间"), "to", time("开始时间上界，不包含该时间")), "doctorId"),
                tool("search_appointments", "查询当前账号可见的预约。普通用户仅能查自己的预约；管理员可查全部。",
                        fields("page", page(), "size", size(), "status", status(),
                                "customerName", text("预约人姓名，精确匹配；null 表示不筛选", 100, true),
                                "from", time("预约开始时间下界，包含该时间"), "to", time("预约开始时间上界，不包含该时间"))),
                tool("get_appointment", "按 ID 查询当前账号可见的预约。", fields("appointmentId", id("预约 ID")), "appointmentId"),
                tool("get_appointment_history", "查询当前账号可见预约的操作记录，返回分页结果。",
                        fields("appointmentId", id("预约 ID"), "page", page(), "size", size()), "appointmentId"),
                tool("prepare_booking", "生成预约预览，返回动作 ID、有效期和真实医生时段信息。不会创建预约，需用户通过确认接口确认。",
                        fields("customerName", text("预约人姓名", 100, false), "slotId", id("目标时段 ID")), "customerName", "slotId"),
                tool("prepare_cancellation", "生成取消预约预览。不会取消预约，需用户确认；预约改期后旧取消预览不能执行。",
                        fields("appointmentId", id("预约 ID")), "appointmentId"),
                tool("prepare_rescheduling", "生成改期预览，保留原预约直到用户确认。expectedSlotId 必须是查询得到的当前时段 ID。",
                        fields("appointmentId", id("预约 ID"), "expectedSlotId", id("查询得到的当前时段 ID"),
                                "targetSlotId", id("目标时段 ID")), "appointmentId", "expectedSlotId", "targetSlotId"),
                tool("start_rescheduling_workflow", "开始可恢复的改期流程。服务器查询候选并暂停，由用户选择、核对预览和确认。优先用于自然语言改期请求。不会修改预约。",
                        fields("appointmentId", id("查询得到的预约 ID"), "doctorId", id("目标医生 ID；省略则使用原医生"),
                                "from", time("候选开始时间下界"), "to", time("候选开始时间上界；时间范围不超过 31 天")),
                        "appointmentId", "from", "to")
        );
    }

    public List<AgentToolDefinition> definitions() {
        return definitions;
    }

    public AgentToolDefinition require(String name) {
        return definitions.stream().filter(tool -> tool.function().name().equals(name)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ARGUMENT, "工具名称不存在"));
    }

    private static AgentToolDefinition tool(String name, String description, Map<String, JsonNode> properties,
            String... required) {
        ObjectNode parameters = JSON.objectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode fields = parameters.putObject("properties");
        properties.forEach(fields::set);
        var requiredNames = parameters.putArray("required");
        for (String field : required) {
            requiredNames.add(field);
        }
        return new AgentToolDefinition("function", new AgentToolDefinition.FunctionDefinition(name, description, parameters));
    }

    private static Map<String, JsonNode> fields(Object... values) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], (JsonNode) values[index + 1]);
        }
        return result;
    }

    private static ObjectNode integer(String description, long minimum, long maximum) {
        return JSON.objectNode().put("type", "integer").put("description", description)
                .put("minimum", minimum).put("maximum", maximum);
    }

    private static JsonNode id(String description) {
        return integer(description, 1, Long.MAX_VALUE);
    }

    private static JsonNode page() {
        return integer("页号，从 0 开始；省略时为 0", 0, Integer.MAX_VALUE).put("default", 0);
    }

    private static JsonNode size() {
        return integer("每页条数；省略时为 20", 1, 100).put("default", 20);
    }

    private static ObjectNode text(String description, int maxLength, boolean nullable) {
        ObjectNode node = JSON.objectNode().put("description", description).put("maxLength", maxLength);
        if (nullable) {
            node.putArray("type").add("string").add("null");
        } else {
            node.put("type", "string").put("minLength", 1);
        }
        return node;
    }

    private static JsonNode time(String description) {
        return text(description + "；ISO 8601 格式且必须带时区，null 表示不筛选", 64, true).put("format", "date-time");
    }

    private static JsonNode status() {
        ObjectNode node = text("BOOKED 或 CANCELLED，null 表示不筛选", 20, true);
        node.putArray("enum").add("BOOKED").add("CANCELLED").addNull();
        return node;
    }
}
