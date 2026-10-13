package com.op1ed.appointmentmanager.agent.application;

public record AgentToolExecution(String callId, String name, boolean success, String errorCode) {
}
