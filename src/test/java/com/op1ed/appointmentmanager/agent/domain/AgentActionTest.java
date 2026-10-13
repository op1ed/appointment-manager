package com.op1ed.appointmentmanager.agent.domain;

import java.time.Instant;
import java.util.UUID;

import com.op1ed.appointmentmanager.shared.error.BusinessException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentActionTest {
    private final Instant created = Instant.parse("2026-10-13T09:00:00Z");
    private final Instant expires = created.plusSeconds(300);

    @Test
    void shouldExpireAtExactBoundary() {
        AgentAction action = pending();
        assertThat(action.isExpiredAt(expires.minusNanos(1))).isFalse();
        assertThat(action.isExpiredAt(expires)).isTrue();
        assertThatThrownBy(() -> action.confirm("{}", expires)).isInstanceOf(BusinessException.class);
        assertThat(action.getStatus()).isEqualTo(AgentActionStatus.PENDING);
    }

    @Test
    void shouldNeverExpireConfirmedActionOrOverwriteFirstResult() {
        AgentAction action = pending();
        action.confirm("{\"id\":123}", created.plusSeconds(1));
        assertThat(action.isExpiredAt(expires.plusSeconds(1))).isFalse();
        assertThatThrownBy(() -> action.confirm("{\"id\":456}", created.plusSeconds(2))).isInstanceOf(BusinessException.class);
        assertThat(action.getResultJson()).isEqualTo("{\"id\":123}");
    }

    @Test
    void shouldRejectNonPositiveLifetime() {
        assertThatThrownBy(() -> new AgentAction(UUID.randomUUID().toString(), 1L, AgentActionKind.BOOK, null,
                "甲", null, 1L, "{}", created, created)).isInstanceOf(IllegalArgumentException.class);
    }

    private AgentAction pending() {
        return new AgentAction(UUID.randomUUID().toString(), 1L, AgentActionKind.BOOK, null,
                "甲", null, 1L, "{}", created, expires);
    }
}
