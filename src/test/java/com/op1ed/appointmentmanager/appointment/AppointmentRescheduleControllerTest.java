package com.op1ed.appointmentmanager.appointment;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentAction;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentHistoryEventResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentHistoryPageResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.identity.web.dto.UserResponse;
import com.op1ed.appointmentmanager.support.AppointmentApiFixtures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentRescheduleControllerTest extends AppointmentApiFixtures {
    @Test
    void shouldAllowNormalOwnerToRescheduleOwnAppointment() {
        UserClient user = normalUser();
        var slots = createSlots(2);
        AppointmentResponse original = book(user.client(), slots.get(0).id());
        assertThat(reschedule(user.client(), original, slots.get(1).id()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail(user.client(), original.id()).slotId()).isEqualTo(slots.get(1).id());
        assertThat(history(user.client(), original.id()).content())
                .extracting(AppointmentHistoryEventResponse::actorId).containsOnly(user.identity().id());
    }

    @Test
    void shouldMoveToAnotherDoctorKeepOwnershipAndRecordActualActors() {
        UserClient user = normalUser();
        var source = createSlots(1).get(0);
        var target = createSlots(1).get(0);
        AppointmentResponse original = book(user.client(), source.id());
        Instant before = Instant.now().minusSeconds(1);
        var moved = restTemplate.postForEntity(path(original.id()) + "/reschedule", Map.of(
                "expectedSlotId", source.id(), "targetSlotId", target.id(),
                "actorId", user.identity().id(), "ownerId", 999999, "customerName", "伪造姓名"
        ), AppointmentResponse.class);
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(moved.getBody()).isNotNull();
        AppointmentResponse current = detail(user.client(), original.id());
        assertThat(current).isEqualTo(moved.getBody());
        assertThat(current.id()).isEqualTo(original.id());
        assertThat(current.customerName()).isEqualTo(original.customerName());
        assertThat(current.slotId()).isEqualTo(target.id());
        assertThat(current.startTime()).isEqualTo(target.startTime());
        assertThat(current.endTime()).isEqualTo(target.endTime());
        assertThat(current.doctorName()).isNotEqualTo(original.doctorName());
        assertThat(current.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(db.queryForObject("SELECT owner_id FROM appointments WHERE id = ?", Long.class, current.id()))
                .isEqualTo(user.identity().id());
        assertThat(activeCount(source.id())).isZero();
        assertThat(activeCount(target.id())).isEqualTo(1L);
        book(user.client(), source.id());
        assertThat(user.client().postForEntity(path(current.id()) + "/cancel", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(activeCount(target.id())).isZero();
        assertThat(activeCount(source.id())).isEqualTo(1L);
        Long adminId = restTemplate.getForEntity("/api/auth/me", UserResponse.class).getBody().id();
        var events = history(user.client(), current.id()).content();
        assertThat(events).extracting(AppointmentHistoryEventResponse::action)
                .containsExactly(AppointmentAction.BOOKED, AppointmentAction.RESCHEDULED, AppointmentAction.CANCELLED);
        assertThat(events).extracting(AppointmentHistoryEventResponse::actorId)
                .containsExactly(user.identity().id(), adminId, user.identity().id());
        assertThat(events.get(0).fromSlotId()).isNull();
        assertThat(events.get(0).toSlotId()).isEqualTo(source.id());
        assertThat(events.get(1).fromSlotId()).isEqualTo(source.id());
        assertThat(events.get(1).toSlotId()).isEqualTo(target.id());
        assertThat(events.get(1).occurredAt().toInstant()).isBetween(before, Instant.now());
        assertThat(events.get(1).occurredAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(events.get(2).fromSlotId()).isEqualTo(target.id());
        assertThat(events.get(2).toSlotId()).isNull();
    }

    @Test
    void shouldAllowRepeatedRequestAtTargetWithoutAdditionalEventAndRejectStaleNewTarget() throws Exception {
        var slots = createSlots(3);
        AppointmentResponse original = book(restTemplate, slots.get(0).id());
        assertThat(reschedule(restTemplate, original, slots.get(1).id()).getStatusCode()).isEqualTo(HttpStatus.OK);
        AppointmentResponse moved = detail(restTemplate, original.id());
        assertThat(reschedule(restTemplate, original, slots.get(1).id()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reschedule(restTemplate, moved, moved.slotId()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertProblem(reschedule(restTemplate, original, slots.get(2).id()), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(detail(restTemplate, original.id())).isEqualTo(moved);
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(2L);
        assertThat(activeCount(slots.get(2).id())).isZero();
    }

    @Test
    void shouldKeepOriginalAppointmentAndHistoryWhenTargetIsOccupiedMissingOrAlreadyStarted() throws Exception {
        var slots = createSlots(3);
        AppointmentResponse original = book(restTemplate, slots.get(0).id());
        book(restTemplate, slots.get(1).id());
        assertProblem(reschedule(restTemplate, original, slots.get(1).id()), HttpStatus.CONFLICT, "CONFLICT");
        assertProblem(reschedule(restTemplate, original, Long.MAX_VALUE), HttpStatus.NOT_FOUND, "NOT_FOUND");
        db.update("UPDATE appointment_slots SET start_time = ?, end_time = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(3600)), Timestamp.from(Instant.now().minusSeconds(1800)),
                slots.get(2).id());
        assertProblem(reschedule(restTemplate, original, slots.get(2).id()), HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        assertThat(detail(restTemplate, original.id())).isEqualTo(original);
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(1L);
        assertThat(activeCount(original.slotId())).isEqualTo(1L);
        assertThat(activeCount(slots.get(2).id())).isZero();
    }

    @Test
    void shouldRejectStartedAndCancelledAppointmentsWithoutReactivatingOrMovingThem() throws Exception {
        var slots = createSlots(2);
        AppointmentResponse original = book(restTemplate, slots.get(0).id());
        db.update("UPDATE appointments SET start_time = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), original.id());
        AppointmentResponse started = detail(restTemplate, original.id());
        assertProblem(reschedule(restTemplate, started, slots.get(1).id()), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(detail(restTemplate, original.id())).isEqualTo(started);
        restTemplate.postForEntity(path(original.id()) + "/cancel", null, String.class);
        AppointmentResponse cancelled = detail(restTemplate, original.id());
        assertProblem(reschedule(restTemplate, cancelled, slots.get(1).id()), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(detail(restTemplate, original.id())).isEqualTo(cancelled);
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(2L);
        assertThat(activeCount(slots.get(1).id())).isZero();
    }

    @Test
    void shouldProtectHistoryAndReschedulingWithSameOwnershipRules() throws Exception {
        UserClient owner = normalUser();
        UserClient stranger = normalUser();
        var slots = createSlots(2);
        AppointmentResponse original = book(owner.client(), slots.get(0).id());
        assertProblem(reschedule(stranger.client(), original, slots.get(1).id()), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(stranger.client().getForEntity(path(original.id()) + "/history", String.class),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(reschedule(anonymousClient(), original, slots.get(1).id()), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertProblem(anonymousClient().getForEntity(path(original.id()) + "/history", String.class),
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertProblem(restTemplate.getForEntity(path(Long.MAX_VALUE) + "/history", String.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(restTemplate.postForEntity(path(Long.MAX_VALUE) + "/reschedule", Map.of(
                "expectedSlotId", original.slotId(), "targetSlotId", slots.get(1).id()
        ), String.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(detail(owner.client(), original.id())).isEqualTo(original);
        assertThat(history(owner.client(), original.id()).totalElements()).isEqualTo(1L);
    }

    @Test
    void shouldKeepOldRecordsWithoutInventingPreUpgradeEvents() throws Exception {
        String name = "历史改期-" + UUID.randomUUID();
        db.update("INSERT INTO appointments (customer_name, doctor_name, start_time, status) VALUES (?, ?, ?, 'BOOKED')",
                name, "历史医生", Timestamp.from(Instant.now().plusSeconds(3600)));
        Long id = db.queryForObject("SELECT id FROM appointments WHERE customer_name = ?", Long.class, name);
        assertThat(history(restTemplate, id).content()).isEmpty();
        assertProblem(restTemplate.postForEntity(path(id) + "/reschedule", Map.of(
                "expectedSlotId", 1, "targetSlotId", Long.MAX_VALUE
        ), String.class), HttpStatus.CONFLICT, "CONFLICT");
        assertThat(restTemplate.postForEntity(path(id) + "/cancel", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history(restTemplate, id).content()).extracting(AppointmentHistoryEventResponse::action)
                .containsExactly(AppointmentAction.CANCELLED);
    }

    @Test
    void shouldPageHistoryWithStableOrderAndCountsIncludingBeyondLastPage() {
        var slots = createSlots(2);
        AppointmentResponse original = book(restTemplate, slots.get(0).id());
        reschedule(restTemplate, original, slots.get(1).id());
        restTemplate.postForEntity(path(original.id()) + "/cancel", null, String.class);
        var defaultPage = history(restTemplate, original.id());
        assertThat(defaultPage.page()).isZero();
        assertThat(defaultPage.size()).isEqualTo(20);
        assertThat(defaultPage.totalElements()).isEqualTo(3L);
        for (int index = 0; index < 4; index++) {
            var page = restTemplate.getForEntity(path(original.id()) + "/history?page={page}&size=1",
                    AppointmentHistoryPageResponse.class, index).getBody();
            assertThat(page).isNotNull();
            assertThat(page.totalElements()).isEqualTo(3L);
            assertThat(page.totalPages()).isEqualTo(3);
            assertThat(page.hasNext()).isEqualTo(index < 2);
            if (index < 3) {
                assertThat(page.content()).containsExactly(defaultPage.content().get(index));
            } else {
                assertThat(page.content()).isEmpty();
            }
        }
        var largest = restTemplate.getForEntity(path(original.id()) + "/history?page=2147483647&size=1",
                AppointmentHistoryPageResponse.class).getBody();
        assertThat(largest).isNotNull();
        assertThat(largest.content()).isEmpty();
        assertThat(largest.hasNext()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void shouldRejectInvalidRescheduleRequestWithoutChangingAppointment(Map<String, Object> request) throws Exception {
        var slots = createSlots(1);
        AppointmentResponse original = book(restTemplate, slots.get(0).id());
        assertProblem(restTemplate.postForEntity(path(original.id()) + "/reschedule", request, String.class),
                HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
        assertThat(detail(restTemplate, original.id())).isEqualTo(original);
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=2147483647&size=2", "page=abc"})
    void shouldRejectInvalidHistoryPage(String query) throws Exception {
        assertProblem(restTemplate.getForEntity(path(Long.MAX_VALUE) + "/history?" + query, String.class),
                HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
    }

    @Test
    void shouldApplyOnlyOneOfTwoDifferentConcurrentReschedules() throws Exception {
        var slots = createSlots(3);
        AppointmentResponse original = book(restTemplate, slots.get(0).id());
        var responses = sendTogether(() -> reschedule(restTemplate, original, slots.get(1).id()),
                () -> reschedule(restTemplate, original, slots.get(2).id()));
        assertThat(responses).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        AppointmentResponse finalState = detail(restTemplate, original.id());
        assertThat(finalState.slotId()).isIn(slots.get(1).id(), slots.get(2).id());
        assertThat(activeCount(original.slotId())).isZero();
        assertThat(activeCount(slots.get(1).id()) + activeCount(slots.get(2).id())).isEqualTo(1L);
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(2L);
    }

    @Test
    void shouldDeduplicateTwoConcurrentIdenticalReschedules() throws Exception {
        var slots = createSlots(2);
        AppointmentResponse original = book(restTemplate, slots.get(0).id());
        var responses = sendTogether(() -> reschedule(restTemplate, original, slots.get(1).id()),
                () -> reschedule(restTemplate, original, slots.get(1).id()));
        assertThat(responses).extracting(ResponseEntity::getStatusCode).containsOnly(HttpStatus.OK);
        assertThat(responses.get(0).getBody()).isEqualTo(responses.get(1).getBody());
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(2L);
    }

    @Test
    void shouldAllowOnlyOneAppointmentToMoveToSharedTarget() throws Exception {
        var slots = createSlots(3);
        AppointmentResponse first = book(restTemplate, slots.get(0).id());
        AppointmentResponse second = book(restTemplate, slots.get(1).id());
        var responses = sendTogether(() -> reschedule(restTemplate, first, slots.get(2).id()),
                () -> reschedule(restTemplate, second, slots.get(2).id()));
        assertThat(responses).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        assertThat(activeCount(slots.get(2).id())).isEqualTo(1L);
        assertThat(activeCount(slots.get(0).id()) + activeCount(slots.get(1).id())).isEqualTo(1L);
        assertThat(history(restTemplate, first.id()).totalElements() + history(restTemplate, second.id()).totalElements())
                .isEqualTo(3L);
    }

    @Test
    void shouldCoordinateBookingAndReschedulingAtSameTarget() throws Exception {
        var slots = createSlots(2);
        AppointmentResponse original = book(restTemplate, slots.get(0).id());
        var responses = sendTogether(() -> reschedule(restTemplate, original, slots.get(1).id()),
                () -> restTemplate.postForEntity("/api/appointments",
                        new CreateAppointmentRequest("并发目标预约", slots.get(1).id()), String.class));
        if (responses.get(0).getStatusCode() == HttpStatus.OK) {
            assertProblem(responses.get(1), HttpStatus.CONFLICT, "CONFLICT");
            assertThat(detail(restTemplate, original.id()).slotId()).isEqualTo(slots.get(1).id());
            assertThat(activeCount(slots.get(0).id())).isZero();
        } else {
            assertProblem(responses.get(0), HttpStatus.CONFLICT, "CONFLICT");
            assertThat(responses.get(1).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(detail(restTemplate, original.id())).isEqualTo(original);
            assertThat(activeCount(slots.get(0).id())).isEqualTo(1L);
        }
        assertThat(activeCount(slots.get(1).id())).isEqualTo(1L);
    }

    @Test
    void shouldSerializeCancellationWithReschedulingAndKeepFinalCancellation() throws Exception {
        var slots = createSlots(2);
        AppointmentResponse original = book(restTemplate, slots.get(0).id());
        var responses = sendTogether(() -> reschedule(restTemplate, original, slots.get(1).id()),
                () -> restTemplate.postForEntity(path(original.id()) + "/cancel", null, String.class));
        assertThat(responses.get(1).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responses.get(0).getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CONFLICT);
        AppointmentResponse finalState = detail(restTemplate, original.id());
        assertThat(finalState.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(activeCount(slots.get(0).id())).isZero();
        assertThat(activeCount(slots.get(1).id())).isZero();
        var events = history(restTemplate, original.id()).content();
        if (responses.get(0).getStatusCode() == HttpStatus.OK) {
            assertThat(finalState.slotId()).isEqualTo(slots.get(1).id());
            assertThat(events).extracting(AppointmentHistoryEventResponse::action)
                    .containsExactly(AppointmentAction.BOOKED, AppointmentAction.RESCHEDULED, AppointmentAction.CANCELLED);
        } else {
            assertThat(finalState.slotId()).isEqualTo(slots.get(0).id());
            assertThat(events).extracting(AppointmentHistoryEventResponse::action)
                    .containsExactly(AppointmentAction.BOOKED, AppointmentAction.CANCELLED);
        }
    }

    @Test
    void shouldRejectCrossMovesToOccupiedSlotsWithoutDeadlock() throws Exception {
        var slots = createSlots(2);
        AppointmentResponse first = book(restTemplate, slots.get(0).id());
        AppointmentResponse second = book(restTemplate, slots.get(1).id());
        var responses = sendTogether(() -> reschedule(restTemplate, first, second.slotId()),
                () -> reschedule(restTemplate, second, first.slotId()));
        assertThat(responses).extracting(ResponseEntity::getStatusCode).containsOnly(HttpStatus.CONFLICT);
        assertThat(detail(restTemplate, first.id())).isEqualTo(first);
        assertThat(detail(restTemplate, second.id())).isEqualTo(second);
        assertThat(history(restTemplate, first.id()).totalElements()).isEqualTo(1L);
        assertThat(history(restTemplate, second.id()).totalElements()).isEqualTo(1L);
    }

    @Test
    void shouldRecordOnlyOneEventForTwoConcurrentCancellations() throws Exception {
        AppointmentResponse original = book(restTemplate, createSlots(1).get(0).id());
        var responses = sendTogether(() -> restTemplate.postForEntity(path(original.id()) + "/cancel", null, String.class),
                () -> restTemplate.postForEntity(path(original.id()) + "/cancel", null, String.class));
        assertThat(responses).extracting(ResponseEntity::getStatusCode).containsOnly(HttpStatus.OK);
        assertThat(responses.get(0).getBody()).isEqualTo(responses.get(1).getBody());
        assertThat(history(restTemplate, original.id()).totalElements()).isEqualTo(2L);
    }

    private static Stream<Arguments> invalidRequests() {
        return Stream.of(Arguments.of(Map.of("targetSlotId", 1)), Arguments.of(Map.of("expectedSlotId", 1)),
                Arguments.of(Map.of("expectedSlotId", 0, "targetSlotId", 1)),
                Arguments.of(Map.of("expectedSlotId", -1, "targetSlotId", 1)),
                Arguments.of(Map.of("expectedSlotId", 1, "targetSlotId", 0)),
                Arguments.of(Map.of("expectedSlotId", 1, "targetSlotId", -1)));
    }
}
