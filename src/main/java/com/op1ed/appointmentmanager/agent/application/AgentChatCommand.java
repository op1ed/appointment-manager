package com.op1ed.appointmentmanager.agent.application;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

public record AgentChatCommand(String requestId, String message) {
    public AgentChatCommand {
        requestId = AgentIds.requireUuid(requestId, "请求 ID");
        if (message == null || message.isBlank() || message.length() > 2000) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "消息不能为空，且不能超过 2000 个字符");
        }
        message = message.strip();
    }
}
