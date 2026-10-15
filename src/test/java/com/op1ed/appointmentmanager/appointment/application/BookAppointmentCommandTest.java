package com.op1ed.appointmentmanager.appointment.application;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookAppointmentCommandTest {
    @Test
    void shouldNormalizeNameAndUuidCaseWithoutChangingBookingIntent() {
        var command = new BookAppointmentCommand("  张三  ", 1L, "ABCDEF12-3456-7890-ABCD-EF1234567890");
        assertThat(command.customerName()).isEqualTo("张三");
        assertThat(command.requestId()).isEqualTo("abcdef12-3456-7890-abcd-ef1234567890");
    }

    @Test
    void shouldKeepRequestIdOptionalForExistingCallers() {
        assertThat(new BookAppointmentCommand("张三", 1L).requestId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", " ", "not-a-uuid", "1-1-1-1-1", "00000000000000000000000000000000",
            "00000000-0000-0000-0000-00000000000g", " 00000000-0000-0000-0000-000000000000",
            "00000000-0000-0000-0000-000000000000x"
    })
    void shouldRejectMalformedOrShortenedRequestIds(String requestId) {
        assertThatThrownBy(() -> new BookAppointmentCommand("张三", 1L, requestId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
    }
}
