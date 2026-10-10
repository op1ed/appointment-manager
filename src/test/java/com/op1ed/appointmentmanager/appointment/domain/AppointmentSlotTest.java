package com.op1ed.appointmentmanager.appointment.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentSlotTest {

    @ParameterizedTest
    @MethodSource("invalidTimes")
    void shouldProtectItsTimeRangeWithoutAnApplicationOrHttpLayer(Instant startTime, Instant endTime) {
        assertThatThrownBy(() -> new AppointmentSlot(7L, startTime, endTime))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT)
                );
    }

    @Test
    void shouldNormalizeItsOwnTimesToDatabasePrecision() {
        Instant startTime = Instant.parse("2030-01-01T10:00:00.123456789Z");
        Instant endTime = startTime.plusSeconds(1800);

        AppointmentSlot slot = new AppointmentSlot(7L, startTime, endTime);

        assertThat(slot.getStartTime()).isEqualTo(startTime.truncatedTo(ChronoUnit.MICROS));
        assertThat(slot.getEndTime()).isEqualTo(endTime.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void shouldBecomeUnavailableAtItsStartTime() {
        Instant startTime = Instant.parse("2030-01-01T10:00:00Z");
        AppointmentSlot slot = new AppointmentSlot(7L, startTime, startTime.plusSeconds(1800));

        assertThat(slot.isInFutureAt(startTime.minusNanos(1))).isTrue();
        assertThat(slot.isInFutureAt(startTime)).isFalse();
        assertThat(slot.isInFutureAt(startTime.plusNanos(1))).isFalse();
    }

    private static Stream<Arguments> invalidTimes() {
        Instant startTime = Instant.parse("2030-01-01T10:00:00.123456700Z");
        return Stream.of(
                Arguments.of(null, startTime.plusSeconds(1800)),
                Arguments.of(startTime, null),
                Arguments.of(startTime, startTime),
                Arguments.of(startTime, startTime.minusSeconds(1)),
                Arguments.of(startTime, startTime.plusNanos(100))
        );
    }
}
