package com.op1ed.appointmentmanager.agent.application;

import java.time.Duration;
import java.util.List;

// 应用层只依赖模型能力；HTTP、认证头和 DeepSeek 格式由外层适配器处理。
public interface AgentModel {
    boolean available();

    AgentModelReply complete(List<AgentMessage> messages, List<AgentToolDefinition> tools, Duration timeout);
}
