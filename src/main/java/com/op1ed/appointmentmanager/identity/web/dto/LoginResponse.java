package com.op1ed.appointmentmanager.identity.web.dto;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.op1ed.appointmentmanager.identity.application.AccessTokenResult;

public record LoginResponse(String accessToken, String tokenType, OffsetDateTime expiresAt, UserResponse user) {
    public static LoginResponse from(AccessTokenResult result) {
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresAt().atOffset(ZoneOffset.UTC),
                UserResponse.from(result.user()));
    }

    @Override
    public String toString() {
        return "LoginResponse[accessToken=[REDACTED], expiresAt=" + expiresAt + "]";
    }
}
