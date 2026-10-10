package com.op1ed.appointmentmanager.shared.error;

public final class BusinessInputs {

    private BusinessInputs() {
    }

    public static String requireName(String value, String fieldName) {
        if (value == null) {
            throw invalidArgument(fieldName + "不能为空");
        }

        String normalized = value.strip();
        if (normalized.isBlank()) {
            throw invalidArgument(fieldName + "不能为空");
        }
        if (normalized.length() > 100) {
            throw invalidArgument(fieldName + "不能超过 100 个字符");
        }
        return normalized;
    }

    public static Long requirePositiveId(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw invalidArgument(fieldName + "必须为正整数");
        }
        return value;
    }

    private static BusinessException invalidArgument(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
