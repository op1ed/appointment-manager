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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityService {
    private final UserAccountRepository accounts;
    private final AccessTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final Duration tokenTtl;
    private final String dummyPasswordHash;

    public IdentityService(
            UserAccountRepository accounts,
            AccessTokenRepository tokens,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.token-ttl:PT24H}") Duration tokenTtl
    ) {
        if (tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("令牌有效期必须为正数");
        }
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenTtl = tokenTtl;
        this.dummyPasswordHash = passwordEncoder.encode(AccessTokens.generate());
    }

    @Transactional
    public UserIdentity register(RegisterUserCommand command) {
        if (accounts.existsByUsername(command.username())) {
            throw usernameConflict();
        }
        UserAccount account = new UserAccount(
                command.username(), command.displayName(),
                passwordEncoder.encode(command.password()), AccountRole.USER
        );
        try {
            return toIdentity(accounts.saveAndFlush(account));
        } catch (DataIntegrityViolationException exception) {
            // 唯一约束也保护两个注册请求同时使用同一用户名的情况。
            throw usernameConflict();
        }
    }

    @Transactional
    public AccessTokenResult login(LoginCommand command) {
        Optional<UserAccount> found = accounts.findByUsername(command.username());
        String expectedHash = found.map(UserAccount::getPasswordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(command.password(), expectedHash);
        if (found.isEmpty() || !matches) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        UserAccount account = found.orElseThrow();
        String rawToken = AccessTokens.generate();
        AccessToken saved = tokens.saveAndFlush(new AccessToken(
                account.getId(), AccessTokens.hash(rawToken), Instant.now().plus(tokenTtl)
        ));
        return new AccessTokenResult(rawToken, saved.getExpiresAt(), toIdentity(account));
    }

    public Optional<UserIdentity> authenticate(String rawToken) {
        if (!AccessTokens.isValidFormat(rawToken)) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(AccessTokens.hash(rawToken))
                .filter(token -> token.isValidAt(Instant.now()))
                .flatMap(token -> accounts.findById(token.getUserId()))
                .map(this::toIdentity);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (AccessTokens.isValidFormat(rawToken)) {
            tokens.revokeByTokenHash(AccessTokens.hash(rawToken));
        }
    }

    @Transactional
    public UserIdentity bootstrapAdministrator(RegisterUserCommand command) {
        Optional<UserAccount> existing = accounts.findByUsername(command.username());
        if (existing.isPresent()) {
            UserAccount account = existing.orElseThrow();
            if (account.getRole() != AccountRole.ADMIN) {
                throw new BusinessException(ErrorCode.CONFLICT, "初始化管理员账号与普通账号重名，请更换 APP_ADMIN_USERNAME");
            }
            return toIdentity(account);
        }
        return toIdentity(accounts.saveAndFlush(new UserAccount(
                command.username(), command.displayName(),
                passwordEncoder.encode(command.password()), AccountRole.ADMIN
        )));
    }

    private UserIdentity toIdentity(UserAccount account) {
        return new UserIdentity(account.getId(), account.getUsername(),
                account.getDisplayName(), account.getRole() == AccountRole.ADMIN);
    }

    private BusinessException usernameConflict() {
        return new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
    }
}
