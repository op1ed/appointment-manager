package com.op1ed.appointmentmanager.appointment;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.support.AuthenticatedApiTest;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentPageResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateSlotRequest;
import com.op1ed.appointmentmanager.appointment.web.dto.SlotResponse;
import com.op1ed.appointmentmanager.doctor.web.dto.CreateDoctorRequest;
import com.op1ed.appointmentmanager.doctor.web.dto.DoctorResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentSearchControllerTest extends AuthenticatedApiTest {

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @Test
    void shouldUseDefaultPageAndSize() {
        String customerName = uniqueCustomerName();
        List<AppointmentResponse> created = createAppointments(customerName, 2);

        AppointmentPageResponse page = search(Map.of("customerName", customerName));

        assertIds(page, created);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(2L);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();

        AppointmentPageResponse largestAllowed = search(Map.of(
                "customerName", customerName, "size", 100
        ));
        assertIds(largestAllowed, created);
        assertThat(largestAllowed.size()).isEqualTo(100);
    }

    @Test
    void shouldReturnOrderedPagesWithStableTotalsIncludingBeyondLastPage() {
        String customerName = uniqueCustomerName();
        List<AppointmentResponse> sorted = createAppointments(customerName, 5).stream()
                .sorted(Comparator.comparing(AppointmentResponse::id)).toList();

        for (int pageNumber = 0; pageNumber <= 3; pageNumber++) {
            AppointmentPageResponse page = search(Map.of(
                    "customerName", customerName, "page", pageNumber, "size", 2
            ));
            int fromIndex = Math.min(pageNumber * 2, sorted.size());
            int toIndex = Math.min(fromIndex + 2, sorted.size());

            assertIds(page, sorted.subList(fromIndex, toIndex));
            assertThat(page.page()).isEqualTo(pageNumber);
            assertThat(page.size()).isEqualTo(2);
            assertThat(page.totalElements()).isEqualTo(5L);
            assertThat(page.totalPages()).isEqualTo(3);
            assertThat(page.hasNext()).isEqualTo(pageNumber < 2);
        }
    }

    @Test
    void shouldReturnEmptyPageWithoutOverflowingHasNextAtLargestSupportedOffset() {
        String customerName = uniqueCustomerName();
        createAppointments(customerName, 1);

        AppointmentPageResponse page = search(Map.of(
                "customerName", customerName, "page", Integer.MAX_VALUE, "size", 1
        ));

        assertThat(page.content()).isEmpty();
        assertThat(page.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void shouldFilterBookedAndCancelledAppointments() {
        String customerName = uniqueCustomerName();
        List<AppointmentResponse> created = createAppointments(customerName, 3);
        cancel(created.get(1).id());

        AppointmentPageResponse booked = search(Map.of(
                "customerName", customerName, "status", "BOOKED"
        ));
        assertIds(booked, List.of(created.get(0), created.get(2)));
        assertThat(booked.totalElements()).isEqualTo(2L);
        assertThat(booked.content()).allSatisfy(appointment ->
                assertThat(appointment.status()).isEqualTo(AppointmentStatus.BOOKED)
        );

        AppointmentPageResponse cancelled = search(Map.of(
                "customerName", customerName, "status", "CANCELLED"
        ));
        assertIds(cancelled, List.of(created.get(1)));
        assertThat(cancelled.totalElements()).isEqualTo(1L);
        assertThat(cancelled.content().get(0).status()).isEqualTo(AppointmentStatus.CANCELLED);

        // 同时验证当前页与筛选后的总数，避免 count SQL 丢失筛选条件。
        AppointmentPageResponse bookedCount = search(Map.of(
                "customerName", customerName, "status", "BOOKED", "size", 1
        ));
        assertThat(bookedCount.content()).hasSize(1);
        assertThat(bookedCount.totalElements()).isEqualTo(2L);
        assertThat(bookedCount.totalPages()).isEqualTo(2);
        assertThat(bookedCount.hasNext()).isTrue();

        AppointmentPageResponse cancelledCount = search(Map.of(
                "customerName", customerName, "status", "CANCELLED", "size", 1
        ));
        assertIds(cancelledCount, List.of(created.get(1)));
        assertThat(cancelledCount.totalElements()).isEqualTo(1L);
        assertThat(cancelledCount.hasNext()).isFalse();
    }

    @Test
    void shouldTrimCustomerNameAndUseExactMatchingRatherThanPrefixMatching() {
        String customerName = uniqueCustomerName();
        List<AppointmentResponse> created = createAppointments(customerName, 2);
        createAppointments(customerName + "-other", 1);

        AppointmentPageResponse page = search(Map.of("customerName", "  " + customerName + "  "));
        assertIds(page, created);
        assertThat(page.totalElements()).isEqualTo(2L);

        AppointmentPageResponse noMatch = search(Map.of(
                "customerName", customerName.substring(0, customerName.length() - 1)
        ));
        assertThat(noMatch.content()).isEmpty();
        assertThat(noMatch.totalElements()).isZero();
        assertThat(noMatch.totalPages()).isZero();
        assertThat(noMatch.hasNext()).isFalse();
    }

    @Test
    void shouldIncludeLowerTimeBoundaryAndExcludeUpperBoundaryAfterMicrosecondNormalization() {
        String customerName = uniqueCustomerName();
        List<AppointmentResponse> created = createAppointments(customerName, 4);
        OffsetDateTime from = created.get(1).startTime().withOffsetSameInstant(ZoneOffset.ofHours(8));
        OffsetDateTime to = created.get(3).startTime().withOffsetSameInstant(ZoneOffset.ofHours(8));

        AppointmentPageResponse exact = search(Map.of(
                "customerName", customerName, "from", from, "to", to
        ));
        assertIds(exact, List.of(created.get(1), created.get(2)));

        AppointmentPageResponse normalized = search(Map.of(
                "customerName", customerName, "from", from.plusNanos(499), "to", to.plusNanos(499)
        ));
        assertIds(normalized, List.of(created.get(1), created.get(2)));
        assertThat(normalized.totalElements()).isEqualTo(2L);

        AppointmentPageResponse counted = search(Map.of(
                "customerName", customerName, "from", from, "to", to, "size", 1
        ));
        assertThat(counted.content()).hasSize(1);
        assertThat(counted.totalElements()).isEqualTo(2L);
        assertThat(counted.totalPages()).isEqualTo(2);
        assertThat(counted.hasNext()).isTrue();
    }

    @Test
    void shouldSupportEachTimeBoundaryOnItsOwn() {
        String customerName = uniqueCustomerName();
        List<AppointmentResponse> created = createAppointments(customerName, 4);
        OffsetDateTime boundary = created.get(2).startTime();

        assertIds(search(Map.of("customerName", customerName, "from", boundary)),
                List.of(created.get(2), created.get(3)));
        assertIds(search(Map.of("customerName", customerName, "to", boundary)),
                List.of(created.get(0), created.get(1)));
    }

    @Test
    void shouldApplyCustomerStatusAndTimeFiltersTogether() {
        String customerName = uniqueCustomerName();
        List<AppointmentResponse> created = createAppointments(customerName, 4);
        cancel(created.get(0).id());
        cancel(created.get(1).id());
        cancel(created.get(3).id());

        AppointmentPageResponse page = search(Map.of(
                "customerName", customerName,
                "status", "CANCELLED",
                "from", created.get(1).startTime(),
                "to", created.get(3).startTime()
        ));

        assertIds(page, List.of(created.get(1)));
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.content().get(0).status()).isEqualTo(AppointmentStatus.CANCELLED);

        AppointmentPageResponse counted = search(Map.of(
                "customerName", customerName,
                "status", "CANCELLED",
                "from", created.get(1).startTime(),
                "to", created.get(3).startTime(),
                "size", 1
        ));
        assertIds(counted, List.of(created.get(1)));
        assertThat(counted.totalElements()).isEqualTo(1L);
        assertThat(counted.totalPages()).isEqualTo(1);
        assertThat(counted.hasNext()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void shouldReturnBadRequestWithInvalidArgumentCodeForInvalidQuery(Map<String, Object> query)
            throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(queryUri(query), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("status").asInt()).isEqualTo(400);
        assertThat(problem.path("code").asText()).isEqualTo("INVALID_ARGUMENT");
    }

    private List<AppointmentResponse> createAppointments(String customerName, int count) {
        ResponseEntity<DoctorResponse> doctorResponse = restTemplate.postForEntity(
                "/api/doctors", new CreateDoctorRequest("查询测试医生-" + UUID.randomUUID()),
                DoctorResponse.class
        );
        assertThat(doctorResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DoctorResponse doctor = doctorResponse.getBody();
        assertThat(doctor).isNotNull();
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.ofHours(8)).plusDays(1).withNano(0);
        List<SlotResponse> slots = new ArrayList<>();

        for (int index = 0; index < count; index++) {
            OffsetDateTime start = base.plusMinutes(index * 30L);
            ResponseEntity<SlotResponse> response = restTemplate.postForEntity(
                    "/api/doctors/" + doctor.id() + "/slots",
                    new CreateSlotRequest(start, start.plusMinutes(30)), SlotResponse.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            slots.add(response.getBody());
        }

        List<AppointmentResponse> appointments = new ArrayList<>(Collections.nCopies(count, null));
        // Booking in reverse time order distinguishes id sorting from start-time sorting.
        for (int index = count - 1; index >= 0; index--) {
            ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                    "/api/appointments", new CreateAppointmentRequest(customerName, slots.get(index).id()),
                    AppointmentResponse.class
            );
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            appointments.set(index, response.getBody());
        }
        return List.copyOf(appointments);
    }

    private void cancel(Long id) {
        ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                "/api/appointments/" + id + "/cancel", null, AppointmentResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    private AppointmentPageResponse search(Map<String, Object> query) {
        ResponseEntity<AppointmentPageResponse> response = restTemplate.getForEntity(
                queryUri(query), AppointmentPageResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).isNotNull();
        return response.getBody();
    }

    private URI queryUri(Map<String, Object> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                "http://127.0.0.1:" + port + "/api/appointments"
        );
        query.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
        // Encode variables before expansion so '+' in an offset remains a literal plus sign.
        return builder.encode().buildAndExpand(query).toUri();
    }

    private void assertIds(AppointmentPageResponse page, List<AppointmentResponse> expected) {
        List<Long> expectedIds = expected.stream().map(AppointmentResponse::id).sorted().toList();
        assertThat(page.content()).extracting(AppointmentResponse::id)
                .containsExactlyElementsOf(expectedIds);
    }

    private String uniqueCustomerName() {
        return "query-" + UUID.randomUUID();
    }

    private static Stream<Arguments> invalidQueries() {
        OffsetDateTime start = OffsetDateTime.parse("2030-01-01T10:00:00+08:00");
        return Stream.of(
                Arguments.of(Map.of("page", -1)),
                Arguments.of(Map.of("page", "invalid")),
                Arguments.of(Map.of("size", 0)),
                Arguments.of(Map.of("size", 101)),
                Arguments.of(Map.of("size", "invalid")),
                Arguments.of(Map.of("page", Integer.MAX_VALUE, "size", 100)),
                Arguments.of(Map.of("status", "UNKNOWN")),
                Arguments.of(Map.of("from", "invalid-date")),
                Arguments.of(Map.of("to", "invalid-date")),
                Arguments.of(Map.of("customerName", "   ")),
                Arguments.of(Map.of("customerName", "名".repeat(101))),
                Arguments.of(Map.of("from", start, "to", start.minusSeconds(1))),
                Arguments.of(Map.of("from", start, "to", start)),
                Arguments.of(Map.of("from", start.plusNanos(100), "to", start.plusNanos(200)))
        );
    }
}
