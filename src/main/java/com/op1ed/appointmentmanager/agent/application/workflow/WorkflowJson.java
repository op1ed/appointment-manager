package com.op1ed.appointmentmanager.agent.application.workflow;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Checkpoint 只保存字符串、数字等 JSON 值，避免依赖 Java 对象序列化。 */
@Component
public class WorkflowJson {
    private final ObjectMapper json;
    public WorkflowJson(ObjectMapper json) { this.json = json; }
    public String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法保存流程数据", exception); }
    }
    public <T> T read(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法读取流程数据", exception); }
    }
    public <T> T read(String value, TypeReference<T> type) {
        try { return json.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("无法读取流程数据", exception); }
    }
    public Map<String, Object> state(String value) { return read(value, new TypeReference<>() { }); }
    public static String text(Map<String, Object> state, String key) { return (String) state.getOrDefault(key, ""); }
    public static Long id(Map<String, Object> state, String key) {
        Number number = (Number) state.get(key);
        return number == null || number.longValue() == 0 ? null : number.longValue();
    }
}
