package com.op1ed.appointmentmanager.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentPageResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.AppointmentResponse;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateAppointmentRequest;
import com.op1ed.appointmentmanager.appointment.web.dto.CreateSlotRequest;
import com.op1ed.appointmentmanager.appointment.web.dto.SlotResponse;
import com.op1ed.appointmentmanager.doctor.web.dto.CreateDoctorRequest;
import com.op1ed.appointmentmanager.doctor.web.dto.DoctorResponse;
import com.op1ed.appointmentmanager.identity.application.AccessTokens;
import com.op1ed.appointmentmanager.identity.web.dto.LoginResponse;
import com.op1ed.appointmentmanager.identity.web.dto.UserResponse;
import com.op1ed.appointmentmanager.support.AuthenticatedApiTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAndOwnershipControllerTest extends AuthenticatedApiTest {
    private static final String PASSWORD = "TestUserPassword1!";

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldAllowPublicDoctorQueriesWithoutCreatingSession() {
        var response = anonymousClient().getForEntity("/api/doctors", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @ParameterizedTest
    @MethodSource("protectedRequests")
    void shouldRejectAnonymousRequestsWithConsistentProblem(HttpMethod method, String path) throws Exception {
        var response = anonymousClient().exchange(path, method,
                new HttpEntity<>(Map.of("customerName", "匿名用户", "slotId", 1)), String.class);
        assertProblem(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void shouldRegisterOnlyNormalUserNormalizeUsernameAndStorePasswordHash() throws Exception {
        String username = uniqueUsername();
        var response = anonymousClient().postForEntity("/api/auth/register", Map.of(
                "username", "  " + username.toUpperCase(Locale.ROOT) + "  ",
                "displayName", "  测试用户  ", "password", PASSWORD, "role", "ADMIN"
        ), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode user = objectMapper.readTree(response.getBody());
        assertThat(user.path("username").asText()).isEqualTo(username);
        assertThat(user.path("displayName").asText()).isEqualTo("测试用户");
        assertThat(user.path("role").asText()).isEqualTo("USER");
        assertThat(user.has("password")).isFalse();
        assertThat(user.has("passwordHash")).isFalse();
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_accounts WHERE username = ?", String.class, username
        );
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        var duplicate = anonymousClient().postForEntity("/api/auth/register", Map.of(
                "username", username.toUpperCase(Locale.ROOT), "displayName", "另一人", "password", PASSWORD
        ), String.class);
        assertProblem(duplicate, HttpStatus.CONFLICT, "CONFLICT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_accounts WHERE username = ?",
                Long.class, username)).isEqualTo(1L);
    }

    @Test
    void shouldProtectConcurrentDuplicateRegistrationWithUniqueConstraint() throws Exception {
        String username = uniqueUsername();
        Map<String, Object> request = Map.of("username", username, "displayName", "并发用户", "password", PASSWORD);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return anonymousClient().postForEntity("/api/auth/register", request, String.class);
            });
            var second = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return anonymousClient().postForEntity("/api/auth/register", request, String.class);
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(responses).extracting(ResponseEntity::getStatusCode)
                    .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
            for (var response : responses) {
                if (response.getStatusCode() == HttpStatus.CONFLICT) {
                    assertProblem(response, HttpStatus.CONFLICT, "CONFLICT");
                }
            }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_accounts WHERE username = ?",
                Long.class, username)).isEqualTo(1L);
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    void shouldRejectInvalidRegistrationBeforeSaving(Map<String, Object> request) throws Exception {
        assertProblem(anonymousClient().postForEntity("/api/auth/register", request, String.class),
                HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT");
    }

    @Test
    void shouldReturnSameLoginErrorForUnknownUserAndWrongPasswordWithoutIssuingToken() throws Exception {
        UserResponse user = registerUser();
        Long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_access_tokens WHERE user_id = ?",
                Long.class, user.id());
        var wrong = anonymousClient().postForEntity("/api/auth/login",
                Map.of("username", user.username(), "password", "WrongPassword1!"), String.class);
        var unknown = anonymousClient().postForEntity("/api/auth/login",
                Map.of("username", uniqueUsername(), "password", PASSWORD), String.class);
        assertProblem(wrong, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertProblem(unknown, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertThat(objectMapper.readTree(wrong.getBody()).path("detail").asText())
                .isEqualTo(objectMapper.readTree(unknown.getBody()).path("detail").asText());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_access_tokens WHERE user_id = ?",
                Long.class, user.id())).isEqualTo(before);
    }

    @Test
    void shouldPersistOnlyTokenHashAndRevokeOnlyTheCurrentLogin() throws Exception {
        UserResponse user = registerUser();
        LoginResponse first = login(user.username(), PASSWORD);
        LoginResponse second = login(user.username(), PASSWORD);
        assertThat(first.accessToken()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second.accessToken());
        assertThat(first.tokenType()).isEqualTo("Bearer");
        assertThat(first.expiresAt().toInstant()).isBetween(Instant.now().plusSeconds(86300), Instant.now().plusSeconds(86410));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_access_tokens WHERE user_id = ? AND token_hash = ?",
                Long.class, user.id(), AccessTokens.hash(first.accessToken()))).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_access_tokens WHERE token_hash = ?",
                Long.class, first.accessToken())).isZero();
        TestRestTemplate client = bearerClient(first.accessToken());
        var me = client.getForEntity("/api/auth/me", UserResponse.class);
        assertThat(me.getBody()).isEqualTo(user);
        assertThat(me.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(client.postForEntity("/api/auth/logout", null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertProblem(client.getForEntity("/api/auth/me", String.class), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertProblem(client.getForEntity("/api/appointments", String.class), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertThat(bearerClient(second.accessToken()).getForEntity("/api/auth/me", UserResponse.class).getBody()).isEqualTo(user);
    }

    @Test
    void shouldRejectExpiredTokenAndIgnoreTokenStoredInCookie() throws Exception {
        UserResponse user = registerUser();
        LoginResponse login = login(user.username(), PASSWORD);
        HttpHeaders cookie = new HttpHeaders();
        cookie.add(HttpHeaders.COOKIE, "access_token=" + login.accessToken());
        assertProblem(anonymousClient().exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(cookie), String.class), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        jdbcTemplate.update("UPDATE user_access_tokens SET expires_at = ? WHERE token_hash = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), AccessTokens.hash(login.accessToken()));
        assertProblem(bearerClient(login.accessToken()).getForEntity("/api/auth/me", String.class),
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"garbage", "Bearer ", "Basic Zm9vOmJhcg==", "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void shouldRejectMalformedOrUnknownAuthorization(String authorization) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        assertProblem(anonymousClient().exchange("/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), String.class), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @Test
    void shouldRestrictDoctorAndScheduleWritesToAdministrators() throws Exception {
        UserResponse user = registerUser();
        var client = bearerClient(login(user.username(), PASSWORD).accessToken());
        assertProblem(client.postForEntity("/api/doctors", new CreateDoctorRequest("未授权医生"), String.class),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        DoctorResponse doctor = createDoctor();
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);
        assertProblem(client.postForEntity("/api/doctors/" + doctor.id() + "/slots",
                new CreateSlotRequest(start, start.plusMinutes(30)), String.class), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointment_slots WHERE doctor_id = ?",
                Long.class, doctor.id())).isZero();
    }

    @Test
    void shouldScopePageCountDetailsAndCancellationToAccountEvenForIdenticalCustomerNames() throws Exception {
        UserResponse alice = registerUser();
        UserResponse bob = registerUser();
        TestRestTemplate aliceClient = bearerClient(login(alice.username(), PASSWORD).accessToken());
        TestRestTemplate bobClient = bearerClient(login(bob.username(), PASSWORD).accessToken());
        List<SlotResponse> slots = createSlots(3);
        String name = "同名预约-" + UUID.randomUUID();
        AppointmentResponse first = book(aliceClient, slots.get(0).id(), name);
        // 即使传入他人的 ownerId、ADMIN 字段，归属仍从登录身份取得。
        var forged = bobClient.postForEntity("/api/appointments", Map.of(
                "customerName", name, "slotId", slots.get(1).id(),
                "ownerId", alice.id(), "administrator", true
        ), AppointmentResponse.class);
        assertThat(forged.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AppointmentResponse bobAppointment = forged.getBody();
        assertThat(bobAppointment).isNotNull();
        AppointmentResponse last = book(aliceClient, slots.get(2).id(), name);
        assertThat(jdbcTemplate.queryForObject("SELECT owner_id FROM appointments WHERE id = ?",
                Long.class, bobAppointment.id())).isEqualTo(bob.id());

        var firstPage = aliceClient.getForEntity("/api/appointments?size=1&customerName={name}&ownerId={id}",
                AppointmentPageResponse.class, name, bob.id()).getBody();
        assertThat(firstPage).isNotNull();
        assertThat(firstPage.content()).extracting(AppointmentResponse::id).containsExactly(first.id());
        assertThat(firstPage.totalElements()).isEqualTo(2L);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.hasNext()).isTrue();
        var lastPage = aliceClient.getForEntity("/api/appointments?page=1&size=1&customerName={name}",
                AppointmentPageResponse.class, name).getBody();
        assertThat(lastPage).isNotNull();
        assertThat(lastPage.content()).extracting(AppointmentResponse::id).containsExactly(last.id());
        assertThat(lastPage.totalElements()).isEqualTo(2L);
        assertThat(lastPage.hasNext()).isFalse();
        var bobPage = bobClient.getForEntity("/api/appointments?customerName={name}",
                AppointmentPageResponse.class, name).getBody();
        assertThat(bobPage).isNotNull();
        assertThat(bobPage.content()).extracting(AppointmentResponse::id).containsExactly(bobAppointment.id());
        assertProblem(aliceClient.getForEntity("/api/appointments/" + bobAppointment.id(), String.class),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(aliceClient.postForEntity("/api/appointments/" + bobAppointment.id() + "/cancel", null, String.class),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM appointments WHERE id = ?",
                String.class, bobAppointment.id())).isEqualTo("BOOKED");
        var cancelled = aliceClient.postForEntity("/api/appointments/" + first.id() + "/cancel", null, AppointmentResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aliceClient.postForEntity("/api/appointments/" + first.id() + "/cancel", null, AppointmentResponse.class).getBody())
                .isEqualTo(cancelled.getBody());
        var adminPage = restTemplate.getForEntity("/api/appointments?customerName={name}&size=1",
                AppointmentPageResponse.class, name).getBody();
        assertThat(adminPage).isNotNull();
        assertThat(adminPage.totalElements()).isEqualTo(3L);
        assertThat(restTemplate.postForEntity("/api/appointments/" + bobAppointment.id() + "/cancel", null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldKeepLegacyUnownedAppointmentsVisibleOnlyToAdministrator() throws Exception {
        UserResponse user = registerUser();
        TestRestTemplate client = bearerClient(login(user.username(), PASSWORD).accessToken());
        String name = "历史记录-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO appointments (customer_name, doctor_name, start_time, status) VALUES (?, ?, ?, 'BOOKED')",
                name, "历史医生", Timestamp.from(Instant.now().minusSeconds(3600)));
        Long id = jdbcTemplate.queryForObject("SELECT id FROM appointments WHERE customer_name = ?", Long.class, name);
        var page = client.getForEntity("/api/appointments?customerName={name}", AppointmentPageResponse.class, name).getBody();
        assertThat(page).isNotNull();
        assertThat(page.totalElements()).isZero();
        assertThat(page.content()).isEmpty();
        assertProblem(client.getForEntity("/api/appointments/" + id, String.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertProblem(client.postForEntity("/api/appointments/" + id + "/cancel", null, String.class), HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertThat(restTemplate.getForEntity("/api/appointments/" + id, AppointmentResponse.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.postForEntity("/api/appointments/" + id + "/cancel", null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private UserResponse registerUser() {
        var response = anonymousClient().postForEntity("/api/auth/register", Map.of(
                "username", uniqueUsername(), "displayName", "测试用户", "password", PASSWORD
        ), UserResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private DoctorResponse createDoctor() {
        var response = restTemplate.postForEntity("/api/doctors", new CreateDoctorRequest("权限医生-" + UUID.randomUUID()), DoctorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private List<SlotResponse> createSlots(int count) {
        DoctorResponse doctor = createDoctor();
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).withNano(0);
        return java.util.stream.IntStream.range(0, count).mapToObj(index -> {
            OffsetDateTime start = base.plusMinutes(index * 30L);
            var response = restTemplate.postForEntity("/api/doctors/" + doctor.id() + "/slots",
                    new CreateSlotRequest(start, start.plusMinutes(30)), SlotResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            return response.getBody();
        }).toList();
    }

    private AppointmentResponse book(TestRestTemplate client, Long slotId, String name) {
        var response = client.postForEntity("/api/appointments", new CreateAppointmentRequest(name, slotId), AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private void assertProblem(ResponseEntity<String> response, HttpStatus status, String code) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        JsonNode problem = objectMapper.readTree(response.getBody());
        assertThat(problem.path("status").asInt()).isEqualTo(status.value());
        assertThat(problem.path("code").asText()).isEqualTo(code);
    }

    private static String uniqueUsername() {
        return "user_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static Stream<Arguments> protectedRequests() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/appointments"),
                Arguments.of(HttpMethod.GET, "/api/appointments/1"),
                Arguments.of(HttpMethod.POST, "/api/appointments"),
                Arguments.of(HttpMethod.POST, "/api/appointments/1/cancel"),
                Arguments.of(HttpMethod.POST, "/api/doctors"),
                Arguments.of(HttpMethod.POST, "/api/doctors/1/slots")
        );
    }

    private static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of(Map.of("username", "ab", "displayName", "用户", "password", PASSWORD)),
                Arguments.of(Map.of("username", "非法用户名", "displayName", "用户", "password", PASSWORD)),
                Arguments.of(Map.of("username", "a".repeat(51), "displayName", "用户", "password", PASSWORD)),
                Arguments.of(Map.of("username", uniqueUsername(), "displayName", "  ", "password", PASSWORD)),
                Arguments.of(Map.of("username", uniqueUsername(), "displayName", "名".repeat(101), "password", PASSWORD)),
                Arguments.of(Map.of("username", uniqueUsername(), "displayName", "用户", "password", "short")),
                Arguments.of(Map.of("username", uniqueUsername(), "displayName", "用户", "password", "a".repeat(73))),
                Arguments.of(Map.of("username", uniqueUsername(), "displayName", "用户", "password", "密".repeat(25))),
                Arguments.of(Map.of("username", uniqueUsername(), "displayName", "用户"))
        );
    }
}
