package com.op1ed.appointmentmanager.appointment.domain;

import java.time.Instant;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void shouldKeepOwnerAndCustomerWhenMovingToAnotherDoctor() {
        Appointment appointment = appointment(11L, NOW.plusSeconds(3600));
        AppointmentSlot target = slot(NOW.plusSeconds(7200));
        appointment.rescheduleTo(target, "新医生", NOW);
        assertThat(appointment.getOwnerId()).isEqualTo(31L);
        assertThat(appointment.getCustomerName()).isEqualTo("家人姓名");
        assertThat(appointment.getSlotId()).isEqualTo(12L);
        assertThat(appointment.getDoctorName()).isEqualTo("新医生");
        assertThat(appointment.getStartTime()).isEqualTo(target.getStartTime());
        assertThat(appointment.getEndTime()).isEqualTo(target.getEndTime());
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void shouldNotResurrectCancelledAppointment() {
        Appointment appointment = appointment(11L, NOW.plusSeconds(3600));
        appointment.cancel();
        assertRejectedWithoutMoving(appointment, slot(NOW.plusSeconds(7200)), ErrorCode.CONFLICT);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void shouldRejectLegacyAppointmentWithoutAssignedSlot() {
        assertRejectedWithoutMoving(appointment(null, NOW.plusSeconds(3600)),
                slot(NOW.plusSeconds(7200)), ErrorCode.CONFLICT);
    }

    @Test
    void shouldRejectSourceAtExactStartBoundary() {
        assertRejectedWithoutMoving(appointment(11L, NOW), slot(NOW.plusSeconds(7200)), ErrorCode.CONFLICT);
    }

    @Test
    void shouldRejectTargetAtExactStartBoundary() {
        assertRejectedWithoutMoving(appointment(11L, NOW.plusSeconds(3600)), slot(NOW), ErrorCode.INVALID_ARGUMENT);
    }

    private void assertRejectedWithoutMoving(Appointment appointment, AppointmentSlot target, ErrorCode code) {
        Long originalSlot = appointment.getSlotId();
        Instant originalTime = appointment.getStartTime();
        assertThatThrownBy(() -> appointment.rescheduleTo(target, "新医生", NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
        assertThat(appointment.getSlotId()).isEqualTo(originalSlot);
        assertThat(appointment.getStartTime()).isEqualTo(originalTime);
        assertThat(appointment.getDoctorName()).isEqualTo("原医生");
    }

    private Appointment appointment(Long slotId, Instant start) {
        return new Appointment(31L, "家人姓名", slotId, "原医生", start, start.plusSeconds(1800));
    }

    private AppointmentSlot slot(Instant start) {
        AppointmentSlot slot = new AppointmentSlot(7L, start, start.plusSeconds(1800));
        ReflectionTestUtils.setField(slot, "id", 12L);
        return slot;
    }
}
