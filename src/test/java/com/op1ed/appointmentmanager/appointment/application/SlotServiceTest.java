package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentRepository;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentSlotRepository;
import com.op1ed.appointmentmanager.doctor.application.DoctorInfo;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
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

    @Mock
    private DoctorService doctorService;

    @Mock
    private AppointmentSlotRepository slotRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    private SlotService slotService;

    @BeforeEach
    void setUp() {
        slotService = new SlotService(doctorService, slotRepository, appointmentRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidSlotInputs")
    void shouldRejectInvalidCommandBeforeAnyDatabaseAccess(Instant startTime, Instant endTime) {
        assertThatThrownBy(() -> slotService.create(7L, new CreateSlotCommand(startTime, endTime)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT)
                );

        verifyNoInteractions(doctorService, slotRepository, appointmentRepository);
    }

    @Test
    void shouldRejectPastSlotAfterDoctorLockWithoutAccessingSlotStorage() {
        when(doctorService.lockForScheduling(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        Instant startTime = Instant.now().minusSeconds(3600);
        CreateSlotCommand command = new CreateSlotCommand(startTime, startTime.plusSeconds(1800));

        assertThatThrownBy(() -> slotService.create(7L, command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT)
                );

        verify(doctorService).lockForScheduling(7L);
        verifyNoInteractions(slotRepository, appointmentRepository);
    }

    @Test
    void shouldNotSaveWhenAnotherSlotOverlaps() {
        CreateSlotCommand command = futureCommand();
        when(doctorService.lockForScheduling(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(slotRepository.existsByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThan(
                7L, command.endTime(), command.startTime()
        )).thenReturn(true);

        assertThatThrownBy(() -> slotService.create(7L, command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.CONFLICT)
                );

        InOrder order = inOrder(doctorService, slotRepository);
        order.verify(doctorService).lockForScheduling(7L);
        order.verify(slotRepository).existsByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThan(
                7L, command.endTime(), command.startTime()
        );
        verify(slotRepository, never()).save(any(AppointmentSlot.class));
        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void shouldLockDoctorBeforeCheckingOverlapAndSavingNormalizedTimes() {
        Instant startTime = Instant.now().plusSeconds(3600)
                .truncatedTo(ChronoUnit.SECONDS).plusNanos(123456789);
        Instant endTime = startTime.plusSeconds(1800);
        CreateSlotCommand command = new CreateSlotCommand(startTime, endTime);
        when(doctorService.lockForScheduling(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(slotRepository.existsByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThan(
                7L, command.endTime(), command.startTime()
        )).thenReturn(false);
        when(slotRepository.save(any(AppointmentSlot.class))).thenAnswer(invocation -> {
            AppointmentSlot slot = invocation.getArgument(0);
            ReflectionTestUtils.setField(slot, "id", 11L);
            return slot;
        });

        SlotResult result = slotService.create(7L, command);

        ArgumentCaptor<AppointmentSlot> savedSlot = ArgumentCaptor.forClass(AppointmentSlot.class);
        InOrder order = inOrder(doctorService, slotRepository);
        order.verify(doctorService).lockForScheduling(7L);
        order.verify(slotRepository).existsByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThan(
                7L, command.endTime(), command.startTime()
        );
        order.verify(slotRepository).save(savedSlot.capture());
        order.verifyNoMoreInteractions();
        verifyNoInteractions(appointmentRepository);

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
