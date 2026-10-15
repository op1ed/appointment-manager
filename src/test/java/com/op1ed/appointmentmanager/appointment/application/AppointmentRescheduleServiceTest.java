package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentAction;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentEvent;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentEventMapper;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentMapper;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentSlotMapper;
import com.op1ed.appointmentmanager.doctor.application.DoctorInfo;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentRescheduleServiceTest {
    private static final UserIdentity USER = new UserIdentity(31L, "alice", "Alice", false);
    private static final UserIdentity ADMIN = new UserIdentity(1L, "admin", "Admin", true);

    @Mock private AppointmentMapper appointments;
    @Mock private AppointmentSlotMapper slots;
    @Mock private DoctorService doctors;
    @Mock private AppointmentEventMapper events;
    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointments, slots, doctors, events);
    }

    @ParameterizedTest
    @MethodSource("slotPairs")
    void shouldLockAppointmentThenBothSlotsInAscendingOrderBeforeAnySnapshotQuery(Long originalId, Long targetId) {
        Appointment appointment = appointment(originalId);
        AppointmentSlot original = slot(originalId);
        AppointmentSlot target = slot(targetId);
        when(appointments.lockVisible(21L, USER.id())).thenReturn(Optional.of(appointment));
        when(slots.lockById(originalId)).thenReturn(Optional.of(original));
        when(slots.lockById(targetId)).thenReturn(Optional.of(target));
        when(doctors.requireById(7L)).thenReturn(new DoctorInfo(7L, "新医生"));
        when(appointments.updateById(appointment)).thenReturn(1);

        var result = service.reschedule(USER, 21L, new RescheduleAppointmentCommand(originalId, targetId)).orElseThrow();

        var order = inOrder(appointments, slots, doctors, events);
        order.verify(appointments).lockVisible(21L, USER.id());
        order.verify(slots).lockById(Math.min(originalId, targetId));
        order.verify(slots).lockById(Math.max(originalId, targetId));
        order.verify(doctors).requireById(7L);
        order.verify(appointments).existsOccupied(targetId, AppointmentStatus.BOOKED);
        order.verify(appointments).updateById(appointment);
        ArgumentCaptor<AppointmentEvent> event = ArgumentCaptor.forClass(AppointmentEvent.class);
        order.verify(events).insert(event.capture());
        assertThat(result.id()).isEqualTo(21L);
        assertThat(result.slotId()).isEqualTo(targetId);
        assertThat(result.doctorName()).isEqualTo("新医生");
        assertThat(appointment.getOwnerId()).isEqualTo(USER.id());
        assertThat(event.getValue().getActorId()).isEqualTo(USER.id());
        assertThat(event.getValue().getAction()).isEqualTo(AppointmentAction.RESCHEDULED);
        assertThat(event.getValue().getFromSlotId()).isEqualTo(originalId);
        assertThat(event.getValue().getToSlotId()).isEqualTo(targetId);
    }

    @Test
    void shouldApplyOwnershipBeforeLookingUpTargetSlotOrHistory() {
        assertThat(service.reschedule(USER, 21L, new RescheduleAppointmentCommand(11L, 12L))).isEmpty();
        assertThat(service.history(USER, 21L, new AppointmentHistoryQuery(0, 20))).isEmpty();
        verify(appointments).lockVisible(21L, USER.id());
        verify(appointments).findOwned(21L, USER.id());
        verifyNoInteractions(slots, doctors, events);
    }

    @Test
    void shouldTreatRetryAtTargetAsNoChangeWithoutAnotherEvent() {
        Appointment appointment = appointment(12L);
        when(appointments.lockVisible(21L, USER.id())).thenReturn(Optional.of(appointment));
        assertThat(service.reschedule(USER, 21L, new RescheduleAppointmentCommand(11L, 12L))
                .orElseThrow().slotId()).isEqualTo(12L);
        verifyNoInteractions(slots, doctors, events);
        verify(appointments, never()).updateById(any(Appointment.class));
    }

    @Test
    void shouldRejectStaleExpectedSlotBeforeLockingSlots() {
        when(appointments.lockVisible(21L, USER.id())).thenReturn(Optional.of(appointment(12L)));
        assertConflict(() -> service.reschedule(USER, 21L, new RescheduleAppointmentCommand(11L, 13L)));
        verifyNoInteractions(slots, doctors, events);
    }

    @Test
    void shouldRejectCancelledOrLegacyAppointmentsBeforeLookingUpTarget() {
        Appointment cancelled = appointment(11L);
        cancelled.cancel();
        when(appointments.lockVisible(21L, USER.id())).thenReturn(Optional.of(cancelled));
        assertConflict(() -> service.reschedule(USER, 21L, new RescheduleAppointmentCommand(11L, 12L)));
        when(appointments.lockVisible(21L, USER.id())).thenReturn(Optional.of(appointment(null)));
        assertConflict(() -> service.reschedule(USER, 21L, new RescheduleAppointmentCommand(11L, 12L)));
        verifyNoInteractions(slots, doctors, events);
    }

    @Test
    void shouldNotDuplicateCancellationEventOrLockRebookedSlot() {
        Appointment cancelled = appointment(11L);
        cancelled.cancel();
        when(appointments.lockVisible(21L, null)).thenReturn(Optional.of(cancelled));
        assertThat(service.cancel(ADMIN, 21L).orElseThrow().status()).isEqualTo(AppointmentStatus.CANCELLED);
        verifyNoInteractions(slots, doctors, events);
        verify(appointments, never()).updateById(any(Appointment.class));
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void shouldRejectInvalidCommandBeforeDatabaseAccess(Long expected, Long target) {
        assertThatThrownBy(() -> service.reschedule(USER, 21L, new RescheduleAppointmentCommand(expected, target)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        verifyNoInteractions(appointments, slots, doctors, events);
    }

    @Test
    void shouldRequireIdentityForReschedulingAndHistory() {
        assertThatThrownBy(() -> service.reschedule(null, 21L, new RescheduleAppointmentCommand(11L, 12L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        assertThatThrownBy(() -> service.history(null, 21L, new AppointmentHistoryQuery(0, 20)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(appointments, slots, doctors, events);
    }

    private Appointment appointment(Long slotId) {
        Instant start = Instant.now().plusSeconds(3600);
        Appointment appointment = new Appointment(USER.id(), "代家人预约", slotId, "原医生", start, start.plusSeconds(1800));
        ReflectionTestUtils.setField(appointment, "id", 21L);
        return appointment;
    }

    private AppointmentSlot slot(Long id) {
        Instant start = Instant.now().plusSeconds(7200);
        AppointmentSlot slot = new AppointmentSlot(7L, start, start.plusSeconds(1800));
        ReflectionTestUtils.setField(slot, "id", id);
        return slot;
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    private static Stream<Arguments> slotPairs() {
        return Stream.of(Arguments.of(11L, 12L), Arguments.of(12L, 11L));
    }

    private static Stream<Arguments> invalidCommands() {
        return Stream.of(Arguments.of(null, 12L), Arguments.of(0L, 12L), Arguments.of(-1L, 12L),
                Arguments.of(11L, null), Arguments.of(11L, 0L), Arguments.of(11L, -1L));
    }
}
