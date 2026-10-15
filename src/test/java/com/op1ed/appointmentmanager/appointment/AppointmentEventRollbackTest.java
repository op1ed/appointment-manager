package com.op1ed.appointmentmanager.appointment;

import java.util.UUID;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentEvent;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentBookingRequestMapper;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentEventMapper;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.support.AppointmentApiFixtures;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

class AppointmentEventRollbackTest extends AppointmentApiFixtures {
    @MockitoSpyBean
    private AppointmentEventMapper eventMapper;

    @MockitoSpyBean
    private AppointmentBookingRequestMapper bookingRequestMapper;

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
    void shouldRestoreOriginalSlotAfterRescheduleSqlWhenEventCannotBeSaved() {
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

    @Test
    void shouldRollBackRequestKeyWhenBookingEventCannotBeSavedAndAllowRetry() {
        Long slotId = createSlots(1).get(0).id();
        String key = UUID.randomUUID().toString();
        failEventSave();

        assertThat(createWithKey(key, slotId).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertBookingAndRequestRolledBack(slotId, key);

        reset(eventMapper);
        assertSuccessfulRetry(key, slotId);
    }

    @Test
    void shouldRollBackBookingAndEventWhenResultSnapshotCannotBeSavedAndAllowRetry() {
        Long slotId = createSlots(1).get(0).id();
        String key = UUID.randomUUID().toString();
        // 此时预约 INSERT 和操作记录 INSERT 都已执行，最后保存结果失败也必须整体回滚。
        doThrow(new IllegalStateException("Simulated booking snapshot storage failure"))
                .when(bookingRequestMapper).complete(anyLong(), anyString());

        assertThat(createWithKey(key, slotId).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertBookingAndRequestRolledBack(slotId, key);

        reset(bookingRequestMapper);
        assertSuccessfulRetry(key, slotId);
    }

    private void assertBookingAndRequestRolledBack(Long slotId, String key) {
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_booking_requests WHERE request_id = ?", Long.class, key))
                .isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Long.class, slotId)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_events WHERE to_slot_id = ?", Long.class, slotId))
                .isZero();
    }

    private void assertSuccessfulRetry(String key, Long slotId) {
        var first = createWithKey(key, slotId);
        var retry = createWithKey(key, slotId);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getBody()).isEqualTo(first.getBody());
        assertThat(activeCount(slotId)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM appointment_events WHERE to_slot_id = ?", Long.class, slotId))
                .isEqualTo(1);
    }

    private ResponseEntity<String> createWithKey(String key, Long slotId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", key);
        return restTemplate.postForEntity("/api/appointments",
                new HttpEntity<>(new CreateAppointmentRequest("回滚后重试", slotId), headers), String.class);
    }

    private void failEventSave() {
        // 让失败发生在业务 SQL 已执行之后，验证真实事务会撤销这些数据库变更。
        doThrow(new IllegalStateException("Simulated appointment event storage failure"))
                .when(eventMapper).insert(any(AppointmentEvent.class));
    }
}
