package com.op1ed.appointmentmanager.agent.application;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.agent.chat")
public record AgentChatPolicy(int maxModelCalls, int maxToolCalls, int maxConcurrentRequests,
        int maxContextChars, int maxToolResultChars, Duration timeout, ZoneId zoneId) {
    public AgentChatPolicy {
        if (maxModelCalls < 1 || maxModelCalls > 10 || maxToolCalls < 1 || maxToolCalls > 20
                || maxConcurrentRequests < 1 || maxConcurrentRequests > 16
                || maxContextChars < 8000 || maxContextChars > 200000
                || maxToolResultChars < 1000 || maxToolResultChars > 50000
                || timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(3)) > 0
                || zoneId == null) {
            throw new IllegalArgumentException("Agent 对话限制配置不合法");
        }
    }
}
