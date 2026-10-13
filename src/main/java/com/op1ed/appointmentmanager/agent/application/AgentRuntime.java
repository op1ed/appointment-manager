package com.op1ed.appointmentmanager.agent.application;

import java.util.List;

import com.op1ed.appointmentmanager.identity.application.UserIdentity;

/** Agent 执行端口，业务层不依赖具体框架的 Message、Graph 或 Hook 类型。 */
public interface AgentRuntime {
    boolean available();
    AgentRunResult run(UserIdentity caller, List<AgentMessage> messages);
}
