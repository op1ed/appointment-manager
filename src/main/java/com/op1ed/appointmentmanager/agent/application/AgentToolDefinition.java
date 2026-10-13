package com.op1ed.appointmentmanager.agent.application;

import com.fasterxml.jackson.databind.JsonNode;

public record AgentToolDefinition(String type, FunctionDefinition function) {
    public record FunctionDefinition(String name, String description, JsonNode parameters) {
    }
}
