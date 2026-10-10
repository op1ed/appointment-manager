package com.op1ed.appointmentmanager.shared.error;

import java.util.Objects;

public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public ErrorCode getCode() {
        return code;
    }
}
