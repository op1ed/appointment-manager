package com.op1ed.appointmentmanager.appointment.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentMapper;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentEventMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
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
    private AppointmentMapper appointmentMapper;

    @Mock
    private AppointmentSlotMapper slotMapper;

    @Mock
    private DoctorService doctorService;

    @Mock
    private AppointmentEventMapper eventMapper;

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentMapper, slotMapper, doctorService, eventMapper
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

        verifyNoInteractions(appointmentMapper, slotMapper, doctorService);
    }

    @Test
    void shouldLockSlotBeforeReadingDoctorCheckingOccupancyAndSaving() {
        AppointmentSlot slot = futureSlot();
        when(slotMapper.lockById(11L)).thenReturn(Optional.of(slot));
        when(doctorService.requireById(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(appointmentMapper.existsOccupied(11L, AppointmentStatus.BOOKED))
                .thenReturn(false);
        when(appointmentMapper.insert(any(Appointment.class)))
                .thenAnswer(invocation -> {
                    Appointment appointment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(appointment, "id", 21L);
                    return 1;
                });

        AppointmentResult result = appointmentService.create(ADMIN,
                new BookAppointmentCommand("  张三  ", 11L)
        );

        ArgumentCaptor<Appointment> savedAppointment = ArgumentCaptor.forClass(Appointment.class);
        InOrder order = inOrder(slotMapper, doctorService, appointmentMapper);
        order.verify(slotMapper).lockById(11L);
        order.verify(doctorService).requireById(7L);
        order.verify(appointmentMapper).existsOccupied(11L, AppointmentStatus.BOOKED);
        order.verify(appointmentMapper).insert(savedAppointment.capture());
        order.verifyNoMoreInteractions();

        assertThat(savedAppointment.getValue().getCustomerName()).isEqualTo("张三");
        assertThat(result.customerName()).isEqualTo("张三");
        assertThat(result.doctorName()).isEqualTo("王医生");
        assertThat(result.status()).isEqualTo(AppointmentStatus.BOOKED);
        verify(doctorService, never()).lockForScheduling(anyLong());
    }

    @Test
    void shouldStopWhenSlotDoesNotExist() {
        when(slotMapper.lockById(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.create(ADMIN, new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(slotMapper).lockById(11L);
        verifyNoInteractions(doctorService, appointmentMapper);
    }

    @Test
    void shouldStopWhenSlotHasAlreadyStarted() {
        Instant startTime = Instant.now().minusSeconds(3600);
        AppointmentSlot slot = new AppointmentSlot(7L, startTime, startTime.plusSeconds(1800));
        ReflectionTestUtils.setField(slot, "id", 11L);
        when(slotMapper.lockById(11L)).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> appointmentService.create(ADMIN, new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT)
                );

        verify(slotMapper).lockById(11L);
        verifyNoInteractions(doctorService, appointmentMapper);
    }

    @Test
    void shouldNotSaveWhenSlotIsAlreadyBooked() {
        AppointmentSlot slot = futureSlot();
        when(slotMapper.lockById(11L)).thenReturn(Optional.of(slot));
        when(doctorService.requireById(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(appointmentMapper.existsOccupied(11L, AppointmentStatus.BOOKED))
                .thenReturn(true);

        assertThatThrownBy(() -> appointmentService.create(ADMIN, new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.CONFLICT)
                );

        InOrder order = inOrder(slotMapper, doctorService, appointmentMapper);
        order.verify(slotMapper).lockById(11L);
        order.verify(doctorService).requireById(7L);
        order.verify(appointmentMapper).existsOccupied(11L, AppointmentStatus.BOOKED);
        verify(appointmentMapper, never()).insert(any(Appointment.class));
    }

    @Test
    void shouldNotCheckOccupancyOrSaveWhenDoctorDoesNotExist() {
        when(slotMapper.lockById(11L)).thenReturn(Optional.of(futureSlot()));
        when(doctorService.requireById(7L))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "医生不存在"));

        assertThatThrownBy(() -> appointmentService.create(ADMIN, new BookAppointmentCommand("张三", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(doctorService).requireById(7L);
        verifyNoInteractions(appointmentMapper);
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

        verifyNoInteractions(appointmentMapper, slotMapper, doctorService);
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
        when(appointmentMapper.search(
                any(),
                eq((Long) null),
                eq(AppointmentStatus.BOOKED), eq("张三"),
                eq(fromTime.truncatedTo(ChronoUnit.MICROS)),
                eq(toTime.truncatedTo(ChronoUnit.MICROS))
        )).thenReturn(new Page<Appointment>(2, 2, 5L).setRecords(List.of(legacy, current)));

        AppointmentPageResult result = appointmentService.search(ADMIN, query);

        verify(appointmentMapper).search(
                argThat(page -> page.getCurrent() == 2 && page.getSize() == 2),
                eq((Long) null),
                eq(AppointmentStatus.BOOKED), eq("张三"),
                eq(fromTime.truncatedTo(ChronoUnit.MICROS)),
                eq(toTime.truncatedTo(ChronoUnit.MICROS))
        );
        verifyNoInteractions(slotMapper, doctorService);

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
        when(slotMapper.lockById(11L)).thenReturn(Optional.of(futureSlot()));
        when(doctorService.requireById(7L)).thenReturn(new DoctorInfo(7L, "王医生"));
        when(appointmentMapper.insert(any(Appointment.class))).thenReturn(1);
        appointmentService.create(user, new BookAppointmentCommand("代家人预约", 11L));
        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentMapper).insert(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(user.id());
    }

    @Test
    void shouldRestrictNormalUserSearchBeforeDatabasePagination() {
        UserIdentity user = new UserIdentity(31L, "alice", "Alice", false);
        when(appointmentMapper.search(any(), eq(31L), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(new Page<Appointment>(1, 20, 0L));
        AppointmentPageResult result = appointmentService.search(user, new AppointmentQuery(0, 20, null, null, null, null));
        assertThat(result.content()).isEmpty();
        verify(appointmentMapper).search(argThat(page -> page.getCurrent() == 1 && page.getSize() == 20),
                eq(31L), eq(null), eq(null), eq(null), eq(null));
        verifyNoInteractions(slotMapper, doctorService);
    }

    @Test
    void shouldNotLockSlotOrModifyAppointmentForAnotherOwner() {
        UserIdentity user = new UserIdentity(31L, "alice", "Alice", false);
        when(appointmentMapper.lockVisible(21L, 31L)).thenReturn(Optional.empty());
        assertThat(appointmentService.cancel(user, 21L)).isEmpty();
        verify(appointmentMapper).lockVisible(21L, 31L);
        verify(appointmentMapper, never()).selectById(anyLong());
        verifyNoInteractions(slotMapper, doctorService, eventMapper);
    }

    @Test
    void shouldRejectMissingIdentityBeforeAnyDatabaseAccess() {
        assertThatThrownBy(() -> appointmentService.create(null, new BookAppointmentCommand("用户", 11L)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
        verifyNoInteractions(appointmentMapper, slotMapper, doctorService);
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
