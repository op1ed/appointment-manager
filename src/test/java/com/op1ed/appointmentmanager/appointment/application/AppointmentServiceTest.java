package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentRepository;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentEventRepository;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.AppointmentSlotRepository;
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
import com.op1ed.appointmentmanager.shared.persistence.StoragePage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final UserIdentity ADMIN = new UserIdentity(1L, "admin", "Admin", true);

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentSlotRepository slotRepository;

    @Mock
    private DoctorService doctorService;

    @Mock
    private AppointmentEventRepository eventRepository;

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentRepository, slotRepository, doctorService, eventRepository
        );
    }

    @ParameterizedTest
    @MethodSource("invalidBookingInputs")
    void shouldRejectInvalidCommandBeforeAnyDatabaseAccess(String customerName, Long slotId) {
        assertThatThrownBy(() -> appointmentService.create(ADMIN,
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
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> {
                    Appointment appointment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(appointment, "id", 21L);
                    return appointment;
                });

        AppointmentResult result = appointmentService.create(ADMIN,
                new BookAppointmentCommand("  张三  ", 11L)
        );

        ArgumentCaptor<Appointment> savedAppointment = ArgumentCaptor.forClass(Appointment.class);
        InOrder order = inOrder(slotRepository, doctorService, appointmentRepository);
        order.verify(slotRepository).findByIdForUpdate(11L);
        order.verify(doctorService).requireById(7L);
        order.verify(appointmentRepository).existsBySlotIdAndStatus(11L, AppointmentStatus.BOOKED);
        order.verify(appointmentRepository).save(savedAppointment.capture());
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

        assertThatThrownBy(() -> appointmentService.create(ADMIN, new BookAppointmentCommand("张三", 11L)))
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

        assertThatThrownBy(() -> appointmentService.create(ADMIN, new BookAppointmentCommand("张三", 11L)))
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

        assertThatThrownBy(() -> appointmentService.create(ADMIN, new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.CONFLICT)
                );

        InOrder order = inOrder(slotRepository, doctorService, appointmentRepository);
        order.verify(slotRepository).findByIdForUpdate(11L);
        order.verify(doctorService).requireById(7L);
        order.verify(appointmentRepository).existsBySlotIdAndStatus(11L, AppointmentStatus.BOOKED);
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void shouldNotCheckOccupancyOrSaveWhenDoctorDoesNotExist() {
        when(slotRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(futureSlot()));
        when(doctorService.requireById(7L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "医生不存在"));

        assertThatThrownBy(() -> appointmentService.create(ADMIN, new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(doctorService).requireById(7L);
        verifyNoInteractions(appointmentRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidSearchInputs")
    void shouldRejectInvalidSearchBeforeAnyDatabaseAccess(
            int page, int size, String customerName, Instant fromTime, Instant toTime
    ) {
        assertThatThrownBy(() -> appointmentService.search(ADMIN, new AppointmentQuery(
                page, size, null, customerName, fromTime, toTime
        ))).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT)
        );

        verifyNoInteractions(appointmentRepository, slotRepository, doctorService);
    }

    @Test
    void shouldSearchWithNormalizedFiltersIdSortingAndPreservePageMetadataAndLegacyNullEndTime() {
        Instant fromTime = Instant.parse("2000-01-01T10:00:00.123456789Z");
        Instant toTime = fromTime.plusSeconds(7200);
        AppointmentQuery query = new AppointmentQuery(
                1, 2, AppointmentStatus.BOOKED, "  张三  ", fromTime, toTime
        );
        Appointment legacy = new Appointment(
                "张三", null, "历史医生", fromTime.plusSeconds(600), null
        );
        Appointment current = new Appointment(
                "张三", 11L, "王医生", fromTime.plusSeconds(1200), fromTime.plusSeconds(3000)
        );
        ReflectionTestUtils.setField(legacy, "id", 21L);
        ReflectionTestUtils.setField(current, "id", 22L);
        when(appointmentRepository.search(
                eq((Long) null),
                eq(AppointmentStatus.BOOKED), eq("张三"),
                eq(fromTime.truncatedTo(ChronoUnit.MICROS)),
                eq(toTime.truncatedTo(ChronoUnit.MICROS)), eq(1), eq(2)
        )).thenReturn(new StoragePage<>(List.of(legacy, current), 1, 2, 5L));

        AppointmentPageResult result = appointmentService.search(ADMIN, query);

        verify(appointmentRepository).search(
                eq((Long) null),
                eq(AppointmentStatus.BOOKED), eq("张三"),
                eq(fromTime.truncatedTo(ChronoUnit.MICROS)),
                eq(toTime.truncatedTo(ChronoUnit.MICROS)), eq(1), eq(2)
        );
        verifyNoInteractions(slotRepository, doctorService);

        assertThat(result.content()).extracting(AppointmentResult::id).containsExactly(21L, 22L);
        assertThat(result.content().get(0).slotId()).isNull();
        assertThat(result.content().get(0).endTime()).isNull();
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(5L);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
    }

    private AppointmentSlot futureSlot() {
        Instant startTime = Instant.now().plusSeconds(3600);
        AppointmentSlot slot = new AppointmentSlot(7L, startTime, startTime.plusSeconds(1800));
        ReflectionTestUtils.setField(slot, "id", 11L);
        return slot;
    }

    @Test
    void shouldBindNewAppointmentToAuthenticatedAccount() {
        UserIdentity user = new UserIdentity(31L, "alice", "Alice", false);
        when(slotRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(futureSlot()));
        when(doctorService.requireById(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        appointmentService.create(user, new BookAppointmentCommand("代家人预约", 11L));
        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(user.id());
    }

    @Test
    void shouldRestrictNormalUserSearchBeforeDatabasePagination() {
        UserIdentity user = new UserIdentity(31L, "alice", "Alice", false);
        when(appointmentRepository.search(eq(31L), eq(null), eq(null), eq(null), eq(null), eq(0), eq(20)))
                .thenReturn(new StoragePage<>(List.of(), 0, 20, 0L));
        AppointmentPageResult result = appointmentService.search(user, new AppointmentQuery(0, 20, null, null, null, null));
        assertThat(result.content()).isEmpty();
        verify(appointmentRepository).search(eq(31L), eq(null), eq(null), eq(null), eq(null), eq(0), eq(20));
        verifyNoInteractions(slotRepository, doctorService);
    }

    @Test
    void shouldNotLockSlotOrModifyAppointmentForAnotherOwner() {
        UserIdentity user = new UserIdentity(31L, "alice", "Alice", false);
        when(appointmentRepository.findVisibleByIdForUpdate(21L, 31L)).thenReturn(Optional.empty());
        assertThat(appointmentService.cancel(user, 21L)).isEmpty();
        verify(appointmentRepository).findVisibleByIdForUpdate(21L, 31L);
        verify(appointmentRepository, never()).findById(anyLong());
        verifyNoInteractions(slotRepository, doctorService, eventRepository);
    }

    @Test
    void shouldRejectMissingIdentityBeforeAnyDatabaseAccess() {
        assertThatThrownBy(() -> appointmentService.create(null, new BookAppointmentCommand("用户", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(appointmentRepository, slotRepository, doctorService);
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

    private static Stream<Arguments> invalidSearchInputs() {
        Instant start = Instant.parse("2030-01-01T10:00:00Z");
        return Stream.of(
                Arguments.of(-1, 20, null, null, null),
                Arguments.of(0, 0, null, null, null),
                Arguments.of(0, 101, null, null, null),
                Arguments.of(Integer.MAX_VALUE, 100, null, null, null),
                Arguments.of(0, 20, "   ", null, null),
                Arguments.of(0, 20, "名".repeat(101), null, null),
                Arguments.of(0, 20, null, start, start.minusSeconds(1)),
                Arguments.of(0, 20, null, start, start),
                Arguments.of(0, 20, null, start.plusNanos(100), start.plusNanos(200))
        );
    }
}
