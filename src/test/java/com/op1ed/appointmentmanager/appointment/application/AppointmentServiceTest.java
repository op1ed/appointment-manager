package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentSlotRepository slotRepository;

    @Mock
    private DoctorService doctorService;

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepository, slotRepository, doctorService
        );
    }

    @ParameterizedTest
    @MethodSource("invalidBookingInputs")
    void shouldRejectInvalidCommandBeforeAnyDatabaseAccess(String customerName, Long slotId) {
        assertThatThrownBy(() -> appointmentService.create(
                new BookAppointmentCommand(customerName, slotId)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT)
        );

        verifyNoInteractions(appointmentRepository, slotRepository, doctorService);
    }

    @Test
    void shouldLockSlotBeforeReadingDoctorCheckingOccupancyAndSaving() {
        AppointmentSlot slot = futureSlot();
        when(slotRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(slot));
        when(doctorService.requireById(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(appointmentRepository.existsBySlotIdAndStatus(11L, AppointmentStatus.BOOKED))
                .thenReturn(false);
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenAnswer(invocation -> {
                    Appointment appointment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(appointment, "id", 21L);
                    return appointment;
                });

        AppointmentResult result = appointmentService.create(
                new BookAppointmentCommand("  张三  ", 11L)
        );

        ArgumentCaptor<Appointment> savedAppointment = ArgumentCaptor.forClass(Appointment.class);
        InOrder order = inOrder(slotRepository, doctorService, appointmentRepository);
        order.verify(slotRepository).findByIdForUpdate(11L);
        order.verify(doctorService).requireById(7L);
        order.verify(appointmentRepository).existsBySlotIdAndStatus(11L, AppointmentStatus.BOOKED);
        order.verify(appointmentRepository).saveAndFlush(savedAppointment.capture());
        order.verifyNoMoreInteractions();

        assertThat(savedAppointment.getValue().getCustomerName()).isEqualTo("张三");
        assertThat(result.customerName()).isEqualTo("张三");
        assertThat(result.doctorName()).isEqualTo("王医生");
        assertThat(result.status()).isEqualTo(AppointmentStatus.BOOKED);
        verify(doctorService, never()).lockForScheduling(anyLong());
    }

    @Test
    void shouldStopWhenSlotDoesNotExist() {
        when(slotRepository.findByIdForUpdate(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.create(new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(slotRepository).findByIdForUpdate(11L);
        verifyNoInteractions(doctorService, appointmentRepository);
    }

    @Test
    void shouldStopWhenSlotHasAlreadyStarted() {
        Instant startTime = Instant.now().minusSeconds(3600);
        AppointmentSlot slot = new AppointmentSlot(7L, startTime, startTime.plusSeconds(1800));
        ReflectionTestUtils.setField(slot, "id", 11L);
        when(slotRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> appointmentService.create(new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT)
                );

        verify(slotRepository).findByIdForUpdate(11L);
        verifyNoInteractions(doctorService, appointmentRepository);
    }

    @Test
    void shouldNotSaveWhenSlotIsAlreadyBooked() {
        AppointmentSlot slot = futureSlot();
        when(slotRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(slot));
        when(doctorService.requireById(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(appointmentRepository.existsBySlotIdAndStatus(11L, AppointmentStatus.BOOKED))
                .thenReturn(true);

        assertThatThrownBy(() -> appointmentService.create(new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.CONFLICT)
                );

        InOrder order = inOrder(slotRepository, doctorService, appointmentRepository);
        order.verify(slotRepository).findByIdForUpdate(11L);
        order.verify(doctorService).requireById(7L);
        order.verify(appointmentRepository).existsBySlotIdAndStatus(11L, AppointmentStatus.BOOKED);
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void shouldNotCheckOccupancyOrSaveWhenDoctorDoesNotExist() {
        when(slotRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(futureSlot()));
        when(doctorService.requireById(7L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "医生不存在"));

        assertThatThrownBy(() -> appointmentService.create(new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(doctorService).requireById(7L);
        verifyNoInteractions(appointmentRepository);
    }

    private AppointmentSlot futureSlot() {
        Instant startTime = Instant.now().plusSeconds(3600);
        AppointmentSlot slot = new AppointmentSlot(7L, startTime, startTime.plusSeconds(1800));
        ReflectionTestUtils.setField(slot, "id", 11L);
        return slot;
    }

    private static Stream<Arguments> invalidBookingInputs() {
        return Stream.of(
                Arguments.of(null, 11L),
                Arguments.of("   ", 11L),
                Arguments.of("名".repeat(101), 11L),
                Arguments.of("张三", null),
                Arguments.of("张三", 0L),
                Arguments.of("张三", -1L)
        );
    }
}
