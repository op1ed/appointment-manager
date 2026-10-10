package com.op1ed.appointmentmanager.appointment;

import java.net.URI;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class AppointmentControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCreateAndQueryAppointment() {
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                "张三",
                "王医生",
                OffsetDateTime.now().plusDays(1).withNano(0)
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
        assertThat(created.startTime().toInstant())
                .isEqualTo(request.startTime().toInstant());

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
}