package com.op1ed.appointmentmanager.agent.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExecuteAgentToolRequest(@NotBlank @Size(max = 80) String name, @NotNull JsonNode arguments) {
}
