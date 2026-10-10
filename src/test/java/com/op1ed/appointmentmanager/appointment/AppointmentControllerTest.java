package com.op1ed.appointmentmanager.appointment;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class AppointmentControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateAndQueryAppointment() {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                "张三",
                "王医生",
                OffsetDateTime.now(ZoneOffset.ofHours(8))
                        .plusDays(1)
                        .withNano(0)
        );

        ResponseEntity<AppointmentResponse> createResponse =
                restTemplate.postForEntity(
                        "/api/appointments",
                        request,
                        AppointmentResponse.class
                );

        assertThat(createResponse.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        AppointmentResponse created = createResponse.getBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isPositive();
        assertThat(created.customerName()).isEqualTo("张三");
        assertThat(created.doctorName()).isEqualTo("王医生");
        assertThat(created.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(created.startTime().toInstant())
                .isEqualTo(request.startTime().toInstant());

        String storedCustomerName = jdbcTemplate.queryForObject(
                "SELECT customer_name FROM appointments WHERE id = ?",
                String.class,
                created.id()
        );

        assertThat(storedCustomerName).isEqualTo("张三");

        String storedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE id = ?",
                String.class,
                created.id()
        );

        assertThat(storedStatus).isEqualTo("BOOKED");

        URI location = createResponse.getHeaders().getLocation();

        assertThat(location).isNotNull();

        ResponseEntity<AppointmentResponse> detailResponse =
                restTemplate.getForEntity(
                        location.toString(),
                        AppointmentResponse.class
                );

        assertThat(detailResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody())
                .isEqualTo(created);

        ResponseEntity<AppointmentResponse[]> listResponse =
                restTemplate.getForEntity(
                        "/api/appointments",
                        AppointmentResponse[].class
                );

        assertThat(listResponse.getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody())
                .isNotNull()
                .extracting(AppointmentResponse::id)
                .contains(created.id());
    }

    @Test
    void shouldRejectBlankCustomerName() {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                " ",
                "王医生",
                OffsetDateTime.now().plusDays(1)
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/appointments",
                request,
                String.class
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectPastStartTime() {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                "张三",
                "王医生",
                OffsetDateTime.now().minusDays(1)
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/appointments",
                request,
                String.class
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnNotFoundForUnknownId() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/appointments/" + Long.MAX_VALUE,
                String.class
        );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldCancelAppointmentAndKeepRecord() {
        AppointmentResponse created = createAppointmentForCancellation();

        ResponseEntity<AppointmentResponse> cancelResponse =
                restTemplate.postForEntity(
                        "/api/appointments/" + created.id() + "/cancel",
                        null,
                        AppointmentResponse.class
                );

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        AppointmentResponse cancelled = cancelResponse.getBody();
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(cancelled)
                .usingRecursiveComparison()
                .ignoringFields("status")
                .isEqualTo(created);

        ResponseEntity<AppointmentResponse> detailResponse =
                restTemplate.getForEntity(
                        "/api/appointments/" + created.id(),
                        AppointmentResponse.class
                );

        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detailResponse.getBody()).isEqualTo(cancelled);

        ResponseEntity<AppointmentResponse[]> listResponse =
                restTemplate.getForEntity(
                        "/api/appointments",
                        AppointmentResponse[].class
                );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull().contains(cancelled);

        String storedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE id = ?",
                String.class,
                created.id()
        );

        assertThat(storedStatus).isEqualTo("CANCELLED");
    }

    @Test
    void shouldReturnSameAppointmentWhenCancelledAgain() {
        AppointmentResponse created = createAppointmentForCancellation();
        String cancelPath = "/api/appointments/" + created.id() + "/cancel";

        ResponseEntity<AppointmentResponse> firstResponse =
                restTemplate.postForEntity(
                        cancelPath,
                        null,
                        AppointmentResponse.class
                );

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().id()).isEqualTo(created.id());
        assertThat(firstResponse.getBody().status())
                .isEqualTo(AppointmentStatus.CANCELLED);

        ResponseEntity<AppointmentResponse> secondResponse =
                restTemplate.postForEntity(
                        cancelPath,
                        null,
                        AppointmentResponse.class
                );

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondResponse.getBody()).isEqualTo(firstResponse.getBody());

        String storedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM appointments WHERE id = ?",
                String.class,
                created.id()
        );

        assertThat(storedStatus).isEqualTo("CANCELLED");
    }

    @Test
    void shouldReturnNotFoundWhenCancellingUnknownId() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/appointments/" + Long.MAX_VALUE + "/cancel",
                null,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private AppointmentResponse createAppointmentForCancellation() {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                "取消测试用户",
                "取消测试医生",
                OffsetDateTime.now().plusDays(1).withNano(0)
        );

        ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                "/api/appointments",
                request,
                AppointmentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AppointmentStatus.BOOKED);

        return response.getBody();
    }
}
