package com.op1ed.appointmentmanager.identity.application;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

final class IdentityInputs {
    private IdentityInputs() {
    }

    static String username(String value) {
        if (value == null) {
            throw invalid("用户名不能为空");
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{3,50}")) {
            throw invalid("用户名须为 3 到 50 位字母、数字、下划线或连字符");
        }
        return normalized;
    }

    static String password(String value) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) < 8
                || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalid("密码至少 8 个字符，UTF-8 编码后不能超过 72 字节");
        }
        // 密码保留原样，不裁剪空格、不转换大小写。
        return value;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
