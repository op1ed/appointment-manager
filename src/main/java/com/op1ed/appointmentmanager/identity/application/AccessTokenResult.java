package com.op1ed.appointmentmanager.identity.application;

import java.time.Instant;

public record AccessTokenResult(String accessToken, Instant expiresAt, UserIdentity user) {
    @Override
    public String toString() {
        return "AccessTokenResult[accessToken=[REDACTED], expiresAt=" + expiresAt + "]";
    }
}
