package com.op1ed.appointmentmanager.support;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentHistoryPageResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateSlotRequest;
import com.op1ed.appointmentmanager.appointment.web.dto.RescheduleAppointmentRequest;
import com.op1ed.appointmentmanager.appointment.web.dto.SlotResponse;
import com.op1ed.appointmentmanager.doctor.web.dto.CreateDoctorRequest;
import com.op1ed.appointmentmanager.doctor.web.dto.DoctorResponse;
import com.op1ed.appointmentmanager.identity.web.dto.UserResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AppointmentApiFixtures extends AuthenticatedApiTest {
    @Autowired protected JdbcTemplate db;
    @Autowired private ObjectMapper json;

    protected List<SlotResponse> createSlots(int count) {
        var doctor = restTemplate.postForEntity("/api/doctors",
                new CreateDoctorRequest("改期医生-" + UUID.randomUUID()), DoctorResponse.class);
        assertThat(doctor.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(doctor.getBody()).isNotNull();
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);
        return IntStream.range(0, count).mapToObj(index -> {
            OffsetDateTime start = base.plusMinutes(index * 30L);
            var slot = restTemplate.postForEntity("/api/doctors/" + doctor.getBody().id() + "/slots",
                    new CreateSlotRequest(start, start.plusMinutes(30)), SlotResponse.class);
            assertThat(slot.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(slot.getBody()).isNotNull();
            return slot.getBody();
        }).toList();
    }

    protected UserClient normalUser() {
        String username = "user_" + UUID.randomUUID().toString().replace("-", "");
        String password = "RescheduleTestPassword1!";
        var response = anonymousClient().postForEntity("/api/auth/register", Map.of(
                "username", username, "displayName", "改期用户", "password", password
        ), UserResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return new UserClient(bearerClient(login(username, password).accessToken()), response.getBody());
    }

    protected AppointmentResponse book(TestRestTemplate client, Long slotId) {
        var response = client.postForEntity("/api/appointments",
                new CreateAppointmentRequest("改期预约人-" + UUID.randomUUID(), slotId), AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    protected ResponseEntity<String> reschedule(TestRestTemplate client, AppointmentResponse original, Long target) {
        return client.postForEntity(path(original.id()) + "/reschedule",
                new RescheduleAppointmentRequest(original.slotId(), target), String.class);
    }

    protected AppointmentResponse detail(TestRestTemplate client, Long id) {
        var response = client.getForEntity(path(id), AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    protected AppointmentHistoryPageResponse history(TestRestTemplate client, Long id) {
        var response = client.getForEntity(path(id) + "/history", AppointmentHistoryPageResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    protected long activeCount(Long slotId) {
        return db.queryForObject("SELECT COUNT(*) FROM appointments WHERE slot_id = ? AND status = 'BOOKED'",
                Long.class, slotId);
    }

    protected void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        var problem = json.readTree(response.getBody());
        assertThat(problem.path("status").asInt()).isEqualTo(status.value());
        assertThat(problem.path("code").asText()).isEqualTo(code);
    }

    protected String path(Long id) {
        return "/api/appointments/" + id;
    }

    protected List<ResponseEntity<String>> sendTogether(Callable<ResponseEntity<String>> first,
            Callable<ResponseEntity<String>> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return first.call();
            });
            var right = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return second.call();
            });
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            return List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS));
        }
    }

    protected record UserClient(TestRestTemplate client, UserResponse identity) {
    }
}
