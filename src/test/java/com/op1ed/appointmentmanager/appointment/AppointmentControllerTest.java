package com.op1ed.appointmentmanager.appointment;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateSlotRequest;
import com.op1ed.appointmentmanager.appointment.web.dto.SlotResponse;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.doctor.web.dto.CreateDoctorRequest;
import com.op1ed.appointmentmanager.doctor.web.dto.DoctorResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.client.RestTemplateBuilderConfigurer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppointmentControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndQueryAppointment() {
        DoctorResponse doctor = createDoctor();
        SlotResponse slot = createSlot(doctor.id());

        ResponseEntity<AppointmentResponse> createResponse = restTemplate.postForEntity(
                "/api/appointments",
                new CreateAppointmentRequest("张三", slot.id()),
                AppointmentResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AppointmentResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isPositive();
        assertThat(created.slotId()).isEqualTo(slot.id());
        assertThat(created.customerName()).isEqualTo("张三");
        assertThat(created.doctorName()).isEqualTo(doctor.name());
        assertThat(created.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(created.startTime().toInstant()).isEqualTo(slot.startTime().toInstant());
        assertThat(created.endTime().toInstant()).isEqualTo(slot.endTime().toInstant());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT customer_name FROM appointments WHERE id = ?",
                String.class,
                created.id()
        )).isEqualTo("张三");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT slot_id FROM appointments WHERE id = ?",
                Long.class,
                created.id()
        )).isEqualTo(slot.id());
        assertStoredStatus(created.id(), "BOOKED");

        URI location = createResponse.getHeaders().getLocation();
        assertThat(location).isNotNull();
        ResponseEntity<AppointmentResponse> detailResponse = restTemplate.getForEntity(
                location.toString(), AppointmentResponse.class
        );
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isEqualTo(created);

        ResponseEntity<AppointmentResponse[]> listResponse = restTemplate.getForEntity(
                "/api/appointments", AppointmentResponse[].class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull().contains(created);
    }

    @Test
    void shouldRejectBlankCustomerName() {
        SlotResponse slot = createSlot(createDoctor().id());
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/appointments",
                new CreateAppointmentRequest(" ", slot.id()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countActiveAppointments(slot.id())).isZero();
    }

    @Test
    void shouldRejectPastSlotStartTime() {
        DoctorResponse doctor = createDoctor();
        OffsetDateTime startTime = futureTime().minusDays(2);
        ResponseEntity<String> response = restTemplate.postForEntity(
                slotsPath(doctor.id()),
                new CreateSlotRequest(startTime, startTime.plusMinutes(30)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnNotFoundForUnknownId() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/appointments/" + Long.MAX_VALUE, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldCancelAppointmentAndKeepRecord() {
        AppointmentResponse created = createAppointmentForCancellation();
        AppointmentResponse cancelled = cancelAppointment(created.id());

        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled)
                .usingRecursiveComparison()
                .ignoringFields("status")
                .isEqualTo(created);

        ResponseEntity<AppointmentResponse> detailResponse = restTemplate.getForEntity(
                "/api/appointments/" + created.id(), AppointmentResponse.class
        );
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isEqualTo(cancelled);

        ResponseEntity<AppointmentResponse[]> listResponse = restTemplate.getForEntity(
                "/api/appointments", AppointmentResponse[].class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull().contains(cancelled);
        assertStoredStatus(created.id(), "CANCELLED");
        assertThat(countActiveAppointments(created.slotId())).isZero();
    }

    @Test
    void shouldReturnSameAppointmentWhenCancelledAgain() {
        AppointmentResponse created = createAppointmentForCancellation();
        AppointmentResponse first = cancelAppointment(created.id());
        AppointmentResponse second = cancelAppointment(created.id());

        assertThat(first.id()).isEqualTo(created.id());
        assertThat(first.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(second).isEqualTo(first);
        assertStoredStatus(created.id(), "CANCELLED");
    }

    @Test
    void shouldReturnNotFoundWhenCancellingUnknownId() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/appointments/" + Long.MAX_VALUE + "/cancel", null, String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldCreateAndQueryDoctor() {
        String name = "医生查询测试-" + UUID.randomUUID();
        ResponseEntity<DoctorResponse> createResponse = restTemplate.postForEntity(
                "/api/doctors", new CreateDoctorRequest(name), DoctorResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DoctorResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isPositive();
        assertThat(created.name()).isEqualTo(name);

        URI location = createResponse.getHeaders().getLocation();
        assertThat(location).isNotNull();
        ResponseEntity<DoctorResponse> detailResponse = restTemplate.getForEntity(
                location.toString(), DoctorResponse.class
        );
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isEqualTo(created);

        ResponseEntity<DoctorResponse[]> listResponse = restTemplate.getForEntity(
                "/api/doctors", DoctorResponse[].class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull().contains(created);
    }

    @Test
    void shouldRejectBlankDoctorName() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/doctors", new CreateDoctorRequest(" "), String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldListOnlyRequestedDoctorsSlotsAndTheirAvailability() {
        DoctorResponse doctor = createDoctor();
        DoctorResponse otherDoctor = createDoctor();
        OffsetDateTime startTime = futureTime();
        SlotResponse first = createSlot(doctor.id(), startTime, startTime.plusMinutes(30));
        SlotResponse second = createSlot(
                doctor.id(), startTime.plusMinutes(30), startTime.plusMinutes(60)
        );
        SlotResponse other = createSlot(
                otherDoctor.id(), startTime, startTime.plusMinutes(30)
        );

        assertThat(listSlots(doctor.id()))
                .extracting(SlotResponse::id)
                .containsExactly(first.id(), second.id())
                .doesNotContain(other.id());
        assertThat(listSlots(doctor.id())).allSatisfy(slot -> {
            assertThat(slot.doctorId()).isEqualTo(doctor.id());
            assertThat(slot.available()).isTrue();
        });

        AppointmentResponse booked = book(first.id(), "时段占用测试");
        assertThat(findSlot(doctor.id(), first.id()).available()).isFalse();
        assertThat(findSlot(doctor.id(), second.id()).available()).isTrue();

        cancelAppointment(booked.id());
        assertThat(findSlot(doctor.id(), first.id()).available()).isTrue();
    }

    @Test
    void shouldReturnNotFoundWhenCreatingSlotForUnknownDoctor() {
        OffsetDateTime startTime = futureTime();
        ResponseEntity<String> response = restTemplate.postForEntity(
                slotsPath(Long.MAX_VALUE),
                new CreateSlotRequest(startTime, startTime.plusMinutes(30)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldRejectSlotEndAtOrBeforeStartTime(int endOffsetMinutes) {
        DoctorResponse doctor = createDoctor();
        OffsetDateTime startTime = futureTime();
        ResponseEntity<String> response = restTemplate.postForEntity(
                slotsPath(doctor.id()),
                new CreateSlotRequest(startTime, startTime.plusMinutes(endOffsetMinutes)),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectOverlappingSlotsAndAllowAdjacentSlots() {
        DoctorResponse doctor = createDoctor();
        OffsetDateTime startTime = futureTime();
        SlotResponse first = createSlot(doctor.id(), startTime, startTime.plusMinutes(30));

        List<CreateSlotRequest> overlaps = List.of(
                new CreateSlotRequest(startTime.plusMinutes(15), startTime.plusMinutes(45)),
                new CreateSlotRequest(startTime.minusMinutes(15), startTime.plusMinutes(15)),
                new CreateSlotRequest(startTime.minusMinutes(15), startTime.plusMinutes(45))
        );
        for (CreateSlotRequest request : overlaps) {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    slotsPath(doctor.id()), request, String.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        SlotResponse before = createSlot(
                doctor.id(), startTime.minusMinutes(30), startTime
        );
        SlotResponse after = createSlot(
                doctor.id(), startTime.plusMinutes(30), startTime.plusMinutes(60)
        );
        assertThat(listSlots(doctor.id()))
                .extracting(SlotResponse::id)
                .containsExactly(before.id(), first.id(), after.id());
    }

    @Test
    void shouldReturnNotFoundForUnknownSlot() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/appointments",
                new CreateAppointmentRequest("不存在时段测试", Long.MAX_VALUE),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectMissingSlotId() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/appointments",
                new CreateAppointmentRequest("缺少时段测试", null),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectBookingStartedSlot() {
        DoctorResponse doctor = createDoctor();
        SlotResponse slot = createSlot(doctor.id());

        // Advance this fixture to the past without waiting for wall-clock time.
        jdbcTemplate.update(
                "UPDATE appointment_slots " +
                "SET start_time = UTC_TIMESTAMP(6) - INTERVAL 2 HOUR, " +
                "end_time = UTC_TIMESTAMP(6) - INTERVAL 1 HOUR WHERE id = ?",
                slot.id()
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/appointments",
                new CreateAppointmentRequest("过期时段测试", slot.id()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countActiveAppointments(slot.id())).isZero();
        assertThat(findSlot(doctor.id(), slot.id()).available()).isFalse();
    }

    @Test
    void shouldRejectBookingAlreadyBookedSlot() throws Exception {
        SlotResponse slot = createSlot(createDoctor().id());
        AppointmentResponse first = book(slot.id(), "第一位预约人");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/appointments",
                new CreateAppointmentRequest("第二位预约人", slot.id()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("status").asInt()).isEqualTo(409);
        assertThat(problem.path("code").asText()).isEqualTo("CONFLICT");
        assertThat(countActiveAppointments(slot.id())).isEqualTo(1L);
        assertStoredStatus(first.id(), "BOOKED");
    }

    @Test
    void shouldReleaseCancelledSlotWithoutReleasingLaterBooking() {
        DoctorResponse doctor = createDoctor();
        SlotResponse slot = createSlot(doctor.id());
        AppointmentResponse first = book(slot.id(), "第一位预约人");
        AppointmentResponse cancelledFirst = cancelAppointment(first.id());
        assertThat(findSlot(doctor.id(), slot.id()).available()).isTrue();

        AppointmentResponse second = book(slot.id(), "第二位预约人");
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(cancelAppointment(first.id())).isEqualTo(cancelledFirst);
        assertThat(findSlot(doctor.id(), slot.id()).available()).isFalse();

        ResponseEntity<String> occupiedResponse = restTemplate.postForEntity(
                "/api/appointments",
                new CreateAppointmentRequest("尝试抢占新预约", slot.id()),
                String.class
        );
        assertThat(occupiedResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertStoredStatus(second.id(), "BOOKED");

        cancelAppointment(second.id());
        AppointmentResponse third = book(slot.id(), "第三位预约人");
        assertThat(third.id()).isNotIn(first.id(), second.id());
        assertThat(third.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertStoredStatus(first.id(), "CANCELLED");
        assertStoredStatus(second.id(), "CANCELLED");
        assertThat(countActiveAppointments(slot.id())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ?",
                Long.class,
                slot.id()
        )).isEqualTo(3L);
    }

    @Test
    void shouldAllowOnlyOneConcurrentBookingPerSlot() throws Exception {
        SlotResponse slot = createSlot(createDoctor().id());
        List<ResponseEntity<String>> responses = sendTwoRequestsTogether(
                "/api/appointments",
                new CreateAppointmentRequest("并发预约人一", slot.id()),
                new CreateAppointmentRequest("并发预约人二", slot.id())
        );

        assertThat(responses).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(countActiveAppointments(slot.id())).isEqualTo(1L);
    }

    @Test
    void shouldAllowOnlyOneConcurrentScheduleForDoctor() throws Exception {
        DoctorResponse doctor = createDoctor();
        OffsetDateTime startTime = futureTime();
        CreateSlotRequest request = new CreateSlotRequest(startTime, startTime.plusMinutes(30));

        List<ResponseEntity<String>> responses = sendTwoRequestsTogether(
                slotsPath(doctor.id()), request, request
        );

        assertThat(responses).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointment_slots WHERE doctor_id = ?",
                Long.class,
                doctor.id()
        )).isEqualTo(1L);
    }

    @Test
    void shouldRequireCallerTransactionForDoctorSchedulingLock() {
        assertThatThrownBy(() -> doctorService.lockForScheduling(Long.MAX_VALUE))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void shouldEnforceSingleActiveAppointmentInDatabase() {
        SlotResponse slot = createSlot(createDoctor().id());
        AppointmentResponse created = book(slot.id(), "数据库约束测试");

        // Bypass the HTTP layer to verify that MySQL itself rejects a second booking.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO appointments " +
                "(customer_name, doctor_name, start_time, end_time, status, slot_id) " +
                "SELECT ?, doctor_name, start_time, end_time, 'BOOKED', slot_id " +
                "FROM appointments WHERE id = ?",
                "直接插入的重复预约",
                created.id()
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(countActiveAppointments(slot.id())).isEqualTo(1L);
        assertStoredStatus(created.id(), "BOOKED");
    }

    private List<ResponseEntity<String>> sendTwoRequestsTogether(
            String path, Object firstRequest, Object secondRequest
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        try {
            for (Object request : List.of(firstRequest, secondRequest)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Booking start signal timed out");
                    }
                    return restTemplate.postForEntity(
                            path, request, String.class
                    );
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ResponseEntity<String> first = futures.get(0).get(20, TimeUnit.SECONDS);
            ResponseEntity<String> second = futures.get(1).get(20, TimeUnit.SECONDS);
            return List.of(first, second);
        } finally {
            start.countDown();
            for (Future<?> future : futures) {
                future.cancel(true);
            }
            executor.shutdownNow();
        }
    }

    private DoctorResponse createDoctor() {
        ResponseEntity<DoctorResponse> response = restTemplate.postForEntity(
                "/api/doctors",
                new CreateDoctorRequest("测试医生-" + UUID.randomUUID()),
                DoctorResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isPositive();
        return response.getBody();
    }

    private SlotResponse createSlot(Long doctorId) {
        OffsetDateTime startTime = futureTime();
        return createSlot(doctorId, startTime, startTime.plusMinutes(30));
    }

    private SlotResponse createSlot(
            Long doctorId, OffsetDateTime startTime, OffsetDateTime endTime
    ) {
        ResponseEntity<SlotResponse> response = restTemplate.postForEntity(
                slotsPath(doctorId),
                new CreateSlotRequest(startTime, endTime),
                SlotResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SlotResponse created = response.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isPositive();
        assertThat(created.doctorId()).isEqualTo(doctorId);
        assertThat(created.startTime().toInstant()).isEqualTo(startTime.toInstant());
        assertThat(created.endTime().toInstant()).isEqualTo(endTime.toInstant());
        assertThat(created.available()).isTrue();
        return created;
    }

    private SlotResponse[] listSlots(Long doctorId) {
        ResponseEntity<SlotResponse[]> response = restTemplate.getForEntity(
                slotsPath(doctorId), SlotResponse[].class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private SlotResponse findSlot(Long doctorId, Long slotId) {
        for (SlotResponse slot : listSlots(doctorId)) {
            if (slot.id().equals(slotId)) {
                return slot;
            }
        }
        throw new AssertionError("Created slot was absent from the doctor's slot list");
    }

    private AppointmentResponse book(Long slotId, String customerName) {
        ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                "/api/appointments",
                new CreateAppointmentRequest(customerName, slotId),
                AppointmentResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().slotId()).isEqualTo(slotId);
        assertThat(response.getBody().status()).isEqualTo(AppointmentStatus.BOOKED);
        return response.getBody();
    }

    private AppointmentResponse createAppointmentForCancellation() {
        SlotResponse slot = createSlot(createDoctor().id());
        return book(slot.id(), "取消测试用户");
    }

    private AppointmentResponse cancelAppointment(Long id) {
        ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                "/api/appointments/" + id + "/cancel", null, AppointmentResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private void assertStoredStatus(Long id, String expectedStatus) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE id = ?", String.class, id
        )).isEqualTo(expectedStatus);
    }

    private long countActiveAppointments(Long slotId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Long.class,
                slotId
        );
        assertThat(count).isNotNull();
        return count;
    }

    private String slotsPath(Long doctorId) {
        return "/api/doctors/" + doctorId + "/slots";
    }

    private OffsetDateTime futureTime() {
        return OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(1).withNano(0);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class HttpClientTestConfiguration {

        @Bean
        RestTemplateBuilder restTemplateBuilder(RestTemplateBuilderConfigurer configurer) {
            return configurer.configure(new RestTemplateBuilder())
                    .connectTimeout(Duration.ofSeconds(5))
                    .readTimeout(Duration.ofSeconds(15));
        }
    }
}
