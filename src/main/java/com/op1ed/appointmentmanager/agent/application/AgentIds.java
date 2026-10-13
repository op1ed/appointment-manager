package com.op1ed.appointmentmanager.agent.application;

import java.util.UUID;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

public final class AgentIds {
    private AgentIds() {
    }

    public static String requireUuid(String value, String label) {
        try {
            String normalized = UUID.fromString(value).toString();
            if (!normalized.equals(value)) {
                throw new IllegalArgumentException();
            }
            return normalized;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, label + "必须为标准 UUID");
        }
    }
}
