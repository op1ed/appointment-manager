package com.op1ed.appointmentmanager.identity.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_access_tokens")
public class AccessToken {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String tokenHash;

    private Instant expiresAt;

    protected AccessToken() {
    }

    public AccessToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }

    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }

    public boolean isValidAt(Instant now) {
        return expiresAt.isAfter(now);
    }
}
