package com.op1ed.appointmentmanager.identity.web.dto;

import com.op1ed.appointmentmanager.identity.application.UserIdentity;

public record UserResponse(Long id, String username, String displayName, String role) {
    public static UserResponse from(UserIdentity user) {
        return new UserResponse(user.id(), user.username(), user.displayName(),
                user.administrator() ? "ADMIN" : "USER");
    }
}
