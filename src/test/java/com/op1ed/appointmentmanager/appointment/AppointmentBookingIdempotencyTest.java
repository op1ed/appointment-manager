package com.op1ed.appointmentmanager.appointment;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.appointment.application.AppointmentService;
import com.op1ed.appointmentmanager.appointment.application.BookAppointmentCommand;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.support.AppointmentApiFixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用真实 HTTP、MySQL 唯一约束和事务验证重试行为；mock 不能证明并发去重正确。 */
class AppointmentBookingIdempotencyTest extends AppointmentApiFixtures {
    @Autowired private ObjectMapper json;
    @Autowired private AppointmentService appointments;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void shouldReplayCreatedResponseAndLocationWithoutDuplicatingBookingOrEvent() throws Exception {
        var user = normalUser();
        Long slotId = createSlots(1).get(0).id();
        String key = UUID.randomUUID().toString();
        var first = create(user.client(), key, "张三", slotId);
        var retry = create(user.client(), key, "张三", slotId);

        assertReplay(first, retry);
        var appointment = read(first);
        assertThat(requestCount(key)).isEqualTo(1);
        assertThat(activeCount(slotId)).isEqualTo(1);
        assertThat(history(user.client(), appointment.id()).totalElements()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT response_json FROM appointment_booking_requests WHERE request_id = ?",
                String.class, key)).isNotBlank();
    }

    @Test
    void shouldMatchNormalizedNameAndUppercaseUuid() {
        Long slotId = createSlots(1).get(0).id();
        String key = UUID.randomUUID().toString();
        assertReplay(create(restTemplate, key, "  张三  ", slotId),
                create(restTemplate, key.toUpperCase(Locale.ROOT), "张三", slotId));
        assertThat(requestCount(key)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"另一位预约人", "alice"})
    void shouldRejectDifferentNameIncludingCaseOnlyChanges(String changedName) throws Exception {
        Long slotId = createSlots(1).get(0).id();
        String key = UUID.randomUUID().toString();
        var first = create(restTemplate, key, "Alice", slotId);

        assertProblem(create(restTemplate, key, changedName, slotId), HttpStatus.CONFLICT, "CONFLICT");
        assertReplay(first, create(restTemplate, key, "Alice", slotId));
        assertThat(history(restTemplate, read(first).id()).totalElements()).isEqualTo(1);
    }

    @Test
    void shouldRejectDifferentSlotWithoutOccupyingIt() throws Exception {
        var slots = createSlots(2);
        String key = UUID.randomUUID().toString();
        var first = create(restTemplate, key, "张三", slots.get(0).id());

        assertProblem(create(restTemplate, key, "张三", slots.get(1).id()), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(activeCount(slots.get(1).id())).isZero();
        assertReplay(first, create(restTemplate, key, "张三", slots.get(0).id()));
    }

    @Test
    void shouldScopeKeysToAccountEvenForAdministrator() throws Exception {
        var user = normalUser();
        var other = normalUser();
        var slots = createSlots(3);
        String key = UUID.randomUUID().toString();
        var first = create(user.client(), key, "张三", slots.get(0).id());
        var second = create(other.client(), key, "张三", slots.get(1).id());

        // 管理员也不能拿别人的请求 ID 读到其响应，只能发起自己账号的创建。
        assertProblem(create(restTemplate, key, "张三", slots.get(0).id()), HttpStatus.CONFLICT, "CONFLICT");
        var admin = create(restTemplate, key, "张三", slots.get(2).id());
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(read(first).id()).isNotEqualTo(read(second).id()).isNotEqualTo(read(admin).id());
        assertReplay(first, create(user.client(), key, "张三", slots.get(0).id()));
        assertThat(requestCount(key)).isEqualTo(3);
    }

    @Test
    void shouldRequireAuthenticationBeforeReservingKey() throws Exception {
        String key = UUID.randomUUID().toString();
        assertProblem(create(anonymousClient(), key, "张三", createSlots(1).get(0).id()),
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertThat(requestCount(key)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-uuid", "1-1-1-1-1"})
    void shouldRejectMalformedHeaderBeforeCreatingBooking(String key) throws Exception {
        Long slotId = createSlots(1).get(0).id();
        assertProblem(create(restTemplate, key, "张三", slotId),
                HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        assertThat(activeCount(slotId)).isZero();
    }

    @Test
    void shouldRollBackKeyForMissingSlotAndPermitCorrectedRetry() throws Exception {
        String key = UUID.randomUUID().toString();
        assertProblem(create(restTemplate, key, "张三", Long.MAX_VALUE), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(requestCount(key)).isZero();

        assertThat(create(restTemplate, key, "张三", createSlots(1).get(0).id()).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(requestCount(key)).isEqualTo(1);
    }

    @Test
    void shouldPermitRetryAfterConflictingBookingIsCancelled() throws Exception {
        Long slotId = createSlots(1).get(0).id();
        var occupied = book(restTemplate, slotId);
        String key = UUID.randomUUID().toString();
        assertProblem(create(restTemplate, key, "张三", slotId), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(requestCount(key)).isZero();

        assertThat(restTemplate.postForEntity(path(occupied.id()) + "/cancel", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var first = create(restTemplate, key, "张三", slotId);
        assertReplay(first, create(restTemplate, key, "张三", slotId));
        assertThat(history(restTemplate, read(first).id()).totalElements()).isEqualTo(1);
    }

    @Test
    void shouldReplayOriginalSnapshotAfterCancellationAndNewBooking() throws Exception {
        Long slotId = createSlots(1).get(0).id();
        String key = UUID.randomUUID().toString();
        var first = create(restTemplate, key, "张三", slotId);
        Long id = read(first).id();
        assertThat(restTemplate.postForEntity(path(id) + "/cancel", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var replacement = book(restTemplate, slotId);

        assertReplay(first, create(restTemplate, key, "张三", slotId));
        assertThat(detail(restTemplate, id).status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(detail(restTemplate, replacement.id())).isEqualTo(replacement);
        assertThat(history(restTemplate, id).totalElements()).isEqualTo(2);
        assertThat(activeCount(slotId)).isEqualTo(1);
    }

    @Test
    void shouldReplayOriginalSnapshotAfterRescheduling() throws Exception {
        var slots = createSlots(2);
        String key = UUID.randomUUID().toString();
        var first = create(restTemplate, key, "张三", slots.get(0).id());
        var original = read(first);
        assertThat(reschedule(restTemplate, original, slots.get(1).id()).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertReplay(first, create(restTemplate, key, "张三", slots.get(0).id()));
        assertThat(detail(restTemplate, original.id()).slotId()).isEqualTo(slots.get(1).id());
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(2);
        assertThat(activeCount(slots.get(0).id())).isZero();
    }

    @Test
    void shouldReplaySuccessfulCreationEvenWhenOriginalSlotHasStarted() {
        Long slotId = createSlots(1).get(0).id();
        String key = UUID.randomUUID().toString();
        var first = create(restTemplate, key, "张三", slotId);
        var past = java.sql.Timestamp.from(Instant.now().minusSeconds(7200));
        var end = java.sql.Timestamp.from(Instant.now().minusSeconds(3600));
        db.update("UPDATE appointment_slots SET start_time = ?, end_time = ? WHERE id = ?", past, end, slotId);

        assertReplay(first, create(restTemplate, key, "张三", slotId));
        assertThat(activeCount(slotId)).isEqualTo(1);
    }

    @Test
    void shouldDeduplicateConcurrentIdenticalRequests() throws Exception {
        Long slotId = createSlots(1).get(0).id();
        String key = UUID.randomUUID().toString();
        var responses = sendTogether(() -> create(restTemplate, key, "张三", slotId),
                () -> create(restTemplate, key, "张三", slotId));

        assertReplay(responses.get(0), responses.get(1));
        assertThat(requestCount(key)).isEqualTo(1);
        assertThat(activeCount(slotId)).isEqualTo(1);
        assertThat(history(restTemplate, read(responses.get(0)).id()).totalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldWaitForOpenTransactionThenReplayOrCreateAfterItsRollback(boolean rollbackFirst) throws Exception {
        var user = normalUser();
        var caller = new UserIdentity(user.identity().id(), user.identity().username(), user.identity().displayName(), false);
        Long slotId = createSlots(1).get(0).id();
        String key = UUID.randomUUID().toString();
        var pending = new AtomicReference<Future<ResponseEntity<String>>>();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var original = new TransactionTemplate(transactions).execute(status -> {
                var result = appointments.create(caller, new BookAppointmentCommand("等待提交", slotId, key));
                // create 已写入全部数据，但外层事务仍未提交；第二个 HTTP 请求必须等待。
                var retry = executor.submit(() -> create(user.client(), key, "等待提交", slotId));
                pending.set(retry);
                assertThatThrownBy(() -> retry.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                if (rollbackFirst) {
                    status.setRollbackOnly();
                }
                return AppointmentResponse.from(result);
            });

            var response = pending.get().get(15, TimeUnit.SECONDS);
            var replay = read(response);
            if (rollbackFirst) {
                assertThat(replay.id()).isNotEqualTo(original.id());
            } else {
                assertThat(replay).isEqualTo(original);
            }
            assertReplay(response, create(user.client(), key, "等待提交", slotId));
            assertThat(requestCount(key)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = ?", Long.class, slotId)).isEqualTo(1);
            assertThat(history(user.client(), replay.id()).totalElements()).isEqualTo(1);
        }
    }

    @Test
    void shouldAcceptOnlyOneConcurrentPayloadForSameKey() throws Exception {
        var slots = createSlots(2);
        String key = UUID.randomUUID().toString();
        var responses = sendTogether(() -> create(restTemplate, key, "张三", slots.get(0).id()),
                () -> create(restTemplate, key, "李四", slots.get(1).id()));

        assertThat(responses).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(requestCount(key)).isEqualTo(1);
        assertThat(activeCount(slots.get(0).id()) + activeCount(slots.get(1).id())).isEqualTo(1);
        var winner = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).findFirst().orElseThrow();
        assertThat(history(restTemplate, read(winner).id()).totalElements()).isEqualTo(1);
    }

    @Test
    void shouldStillAllowOnlyOneBookingWhenDifferentKeysRaceForSameSlot() throws Exception {
        Long slotId = createSlots(1).get(0).id();
        String firstKey = UUID.randomUUID().toString();
        String secondKey = UUID.randomUUID().toString();
        var responses = sendTogether(() -> create(restTemplate, firstKey, "张三", slotId),
                () -> create(restTemplate, secondKey, "李四", slotId));

        assertThat(responses).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(activeCount(slotId)).isEqualTo(1);
        assertThat(requestCount(firstKey) + requestCount(secondKey)).isEqualTo(1);
    }

    private ResponseEntity<String> create(TestRestTemplate client, String key, String name, Long slotId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", key);
        return client.postForEntity("/api/appointments",
                new HttpEntity<>(new CreateAppointmentRequest(name, slotId), headers), String.class);
    }

    private long requestCount(String key) {
        return db.queryForObject("SELECT COUNT(*) FROM appointment_booking_requests WHERE request_id = ?", Long.class, key);
    }

    private AppointmentResponse read(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readValue(response.getBody(), AppointmentResponse.class);
    }

    private void assertReplay(ResponseEntity<String> first, ResponseEntity<String> retry) {
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getBody()).isEqualTo(first.getBody());
        assertThat(first.getHeaders().getLocation()).isNotNull();
        assertThat(retry.getHeaders().getLocation()).isEqualTo(first.getHeaders().getLocation());
    }
}
