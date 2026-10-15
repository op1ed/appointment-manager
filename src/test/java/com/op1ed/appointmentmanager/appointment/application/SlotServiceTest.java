package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import com.op1ed.appointmentmanager.appointment.application.impl.SlotServiceImpl;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    private static final UserIdentity ADMIN = new UserIdentity(1L, "admin", "Admin", true);

    @Mock
    private DoctorService doctorService;

    @Mock
    private AppointmentSlotMapper slotMapper;

    @Mock
    private AppointmentMapper appointmentMapper;

    private SlotService slotService;

    @BeforeEach
    void setUp() {
        slotService = new SlotServiceImpl(doctorService, slotMapper, appointmentMapper);
    }

    @ParameterizedTest
    @MethodSource("invalidSlotInputs")
    void shouldRejectInvalidCommandBeforeAnyDatabaseAccess(Instant startTime, Instant endTime) {
        assertThatThrownBy(() -> slotService.create(ADMIN, 7L, new CreateSlotCommand(startTime, endTime)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT)
                );

        verifyNoInteractions(doctorService, slotMapper, appointmentMapper);
    }

    @Test
    void shouldRejectPastSlotAfterDoctorLockWithoutAccessingSlotStorage() {
        when(doctorService.lockForScheduling(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        Instant startTime = Instant.now().minusSeconds(3600);
        CreateSlotCommand command = new CreateSlotCommand(startTime, startTime.plusSeconds(1800));

        assertThatThrownBy(() -> slotService.create(ADMIN, 7L, command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT)
                );

        verify(doctorService).lockForScheduling(7L);
        verifyNoInteractions(slotMapper, appointmentMapper);
    }

    @Test
    void shouldNotSaveWhenAnotherSlotOverlaps() {
        CreateSlotCommand command = futureCommand();
        when(doctorService.lockForScheduling(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(slotMapper.overlaps(
                7L, command.endTime(), command.startTime()
        )).thenReturn(true);

        assertThatThrownBy(() -> slotService.create(ADMIN, 7L, command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.CONFLICT)
                );

        InOrder order = inOrder(doctorService, slotMapper);
        order.verify(doctorService).lockForScheduling(7L);
        order.verify(slotMapper).overlaps(
                7L, command.endTime(), command.startTime()
        );
        verify(slotMapper, never()).insert(any(AppointmentSlot.class));
        verifyNoInteractions(appointmentMapper);
    }

    @Test
    void shouldLockDoctorBeforeCheckingOverlapAndSavingNormalizedTimes() {
        Instant startTime = Instant.now().plusSeconds(3600)
                .truncatedTo(ChronoUnit.SECONDS).plusNanos(123456789);
        Instant endTime = startTime.plusSeconds(1800);
        CreateSlotCommand command = new CreateSlotCommand(startTime, endTime);
        when(doctorService.lockForScheduling(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(slotMapper.overlaps(
                7L, command.endTime(), command.startTime()
        )).thenReturn(false);
        when(slotMapper.insert(any(AppointmentSlot.class))).thenAnswer(invocation -> {
            AppointmentSlot slot = invocation.getArgument(0);
            ReflectionTestUtils.setField(slot, "id", 11L);
            return 1;
        });

        SlotResult result = slotService.create(ADMIN, 7L, command);

        ArgumentCaptor<AppointmentSlot> savedSlot = ArgumentCaptor.forClass(AppointmentSlot.class);
        InOrder order = inOrder(doctorService, slotMapper);
        order.verify(doctorService).lockForScheduling(7L);
        order.verify(slotMapper).overlaps(
                7L, command.endTime(), command.startTime()
        );
        order.verify(slotMapper).insert(savedSlot.capture());
        order.verifyNoMoreInteractions();
        verifyNoInteractions(appointmentMapper);

        assertThat(command.startTime()).isEqualTo(startTime.truncatedTo(ChronoUnit.MICROS));
        assertThat(command.endTime()).isEqualTo(endTime.truncatedTo(ChronoUnit.MICROS));
        assertThat(savedSlot.getValue().getStartTime()).isEqualTo(command.startTime());
        assertThat(savedSlot.getValue().getEndTime()).isEqualTo(command.endTime());
        assertThat(result.startTime()).isEqualTo(command.startTime());
        assertThat(result.endTime()).isEqualTo(command.endTime());
        assertThat(result.available()).isTrue();
    }

    private CreateSlotCommand futureCommand() {
        Instant startTime = Instant.now().plusSeconds(3600);
        return new CreateSlotCommand(startTime, startTime.plusSeconds(1800));
    }

    @Test
    void shouldRejectNonAdministratorBeforeAnyDatabaseAccess() {
        UserIdentity user = new UserIdentity(31L, "alice", "Alice", false);
        assertThatThrownBy(() -> slotService.create(user, 7L, futureCommand()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verifyNoInteractions(doctorService, slotMapper, appointmentMapper);
    }

    private static Stream<Arguments> invalidSlotInputs() {
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
