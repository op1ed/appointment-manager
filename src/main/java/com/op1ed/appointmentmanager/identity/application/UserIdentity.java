package com.op1ed.appointmentmanager.identity.application;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

public record UserIdentity(Long id, String username, String displayName, boolean administrator) {
    public UserIdentity {
        id = BusinessInputs.requirePositiveId(id, "用户 ID");
    }

    public static UserIdentity requireAuthenticated(UserIdentity user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "请先登录");
        }
        return user;
    }

    public void requireAdministrator() {
        if (!administrator) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "需要管理员权限");
        }
    }
}
