package com.op1ed.appointmentmanager.appointment;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentEvent;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentEventRepository;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.support.AppointmentApiFixtures;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class AppointmentEventRollbackTest extends AppointmentApiFixtures {
    @MockitoSpyBean
    private AppointmentEventRepository eventRepository;

    @Test
    void shouldRollBackBookingWhenEventCannotBeSaved() {
        var slot = createSlots(1).get(0);
        failEventSave();
        var response = restTemplate.postForEntity("/api/appointments",
                new CreateAppointmentRequest("回滚预约", slot.id()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Long.class, slot.id())).isZero();
        assertThat(activeCount(slot.id())).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_events WHERE to_slot_id = ?", Long.class, slot.id()))
                .isZero();
    }

    @Test
    void shouldRestoreOriginalSlotAfterFlushedRescheduleWhenEventCannotBeSaved() {
        var slots = createSlots(2);
        var original = book(restTemplate, slots.get(0).id());
        failEventSave();
        assertThat(reschedule(restTemplate, original, slots.get(1).id()).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(detail(restTemplate, original.id())).isEqualTo(original);
        assertThat(activeCount(slots.get(0).id())).isEqualTo(1L);
        assertThat(activeCount(slots.get(1).id())).isZero();
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(1L);
    }

    @Test
    void shouldRestoreBookedStatusWhenCancellationEventCannotBeSaved() {
        var original = book(restTemplate, createSlots(1).get(0).id());
        failEventSave();
        assertThat(restTemplate.postForEntity(path(original.id()) + "/cancel", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(detail(restTemplate, original.id())).isEqualTo(original);
        assertThat(activeCount(original.slotId())).isEqualTo(1L);
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(1L);
    }

    private void failEventSave() {
        // 让失败发生在业务 SQL 已 flush 之后，验证真实事务会撤销这些数据库变更。
        doThrow(new IllegalStateException("Simulated appointment event storage failure"))
                .when(eventRepository).save(any(AppointmentEvent.class));
    }
}
