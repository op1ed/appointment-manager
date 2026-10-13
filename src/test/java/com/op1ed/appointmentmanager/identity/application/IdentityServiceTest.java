package com.op1ed.appointmentmanager.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.op1ed.appointmentmanager.identity.domain.AccessToken;
import com.op1ed.appointmentmanager.identity.domain.AccountRole;
import com.op1ed.appointmentmanager.identity.domain.UserAccount;
import com.op1ed.appointmentmanager.identity.infrastructure.persistence.AccessTokenRepository;
import com.op1ed.appointmentmanager.identity.infrastructure.persistence.UserAccountRepository;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdentityServiceTest {
    private UserAccountRepository accounts;
    private AccessTokenRepository tokens;
    private IdentityService service;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountRepository.class);
        tokens = mock(AccessTokenRepository.class);
        service = new IdentityService(accounts, tokens, new BCryptPasswordEncoder(), Duration.ofHours(24));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "spaces in token", "Bearer token"})
    void shouldRejectMalformedTokensBeforeAnyDatabaseAccess(String token) {
        assertThat(service.authenticate(token)).isEmpty();
        verifyNoInteractions(accounts, tokens);
    }

    @Test
    void shouldRejectExpiredTokenBeforeReadingAccount() {
        String rawToken = AccessTokens.generate();
        when(tokens.findByTokenHash(AccessTokens.hash(rawToken)))
                .thenReturn(Optional.of(new AccessToken(31L, AccessTokens.hash(rawToken), Instant.now().minusSeconds(10))));
        assertThat(service.authenticate(rawToken)).isEmpty();
        verifyNoInteractions(accounts);
    }

    @Test
    void shouldTreatExactExpiryInstantAsExpired() {
        Instant expiry = Instant.parse("2030-01-01T00:00:00.123456Z");
        AccessToken token = new AccessToken(31L, AccessTokens.hash(AccessTokens.generate()), expiry);
        assertThat(token.isValidAt(expiry.minusNanos(1))).isTrue();
        assertThat(token.isValidAt(expiry)).isFalse();
        assertThat(token.isValidAt(expiry.plusNanos(1))).isFalse();
    }

    @Test
    void shouldNeverPromoteExistingNormalUserDuringBootstrap() {
        UserAccount normal = new UserAccount("admin", "用户", "hash", AccountRole.USER);
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(normal));
        assertThatThrownBy(() -> service.bootstrapAdministrator(new RegisterUserCommand("admin", "管理员", "Password1!")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.CONFLICT));
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldKeepExistingAdministratorWithoutResettingPasswordOnRestart() {
        UserAccount admin = new UserAccount("admin", "管理员", "original-hash", AccountRole.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 31L);
        when(accounts.findByUsername("admin")).thenReturn(Optional.of(admin));
        UserIdentity result = service.bootstrapAdministrator(new RegisterUserCommand("admin", "管理员", "NewPassword1!"));
        assertThat(result.administrator()).isTrue();
        assertThat(admin.getPasswordHash()).isEqualTo("original-hash");
        verify(accounts).findByUsername("admin");
        verifyNoInteractions(tokens);
    }

    @Test
    void shouldRejectInvalidRegistrationBeforeAnyDatabaseAccess() {
        assertThatThrownBy(() -> service.register(new RegisterUserCommand("valid-user", "用户", "密".repeat(25))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.INVALID_ARGUMENT));
        verifyNoInteractions(accounts, tokens);
    }
}
