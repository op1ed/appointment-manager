package com.op1ed.appointmentmanager.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.op1ed.appointmentmanager.identity.domain.AccessToken;
import com.op1ed.appointmentmanager.identity.domain.AccountRole;
import com.op1ed.appointmentmanager.identity.domain.UserAccount;
import com.op1ed.appointmentmanager.identity.infrastructure.persistence.mapper.AccessTokenMapper;
import com.op1ed.appointmentmanager.identity.infrastructure.persistence.mapper.UserAccountMapper;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 身份模块的应用服务，组织账号、密码和登录令牌的处理。
 *
 * <p>阅读 3A 先看 register（建立账号）、login（校验密码并发令牌）和 toIdentity（整理身份）。
 * authenticate、revoke 是后续请求验证令牌与退出时使用的，留到 3B 再读。
 * bootstrapAdministrator 是应用启动时初始化管理员的入口，读完普通注册再回来看即可。
 *
 * <p>与 DoctorService 一样，类上的事务声明默认用于查询；写入方法单独声明写事务。
 */
@Service
@Transactional(readOnly = true)
public class IdentityService {
    private final UserAccountMapper accounts; // 查询、保存 user_accounts 中的账号。
    private final AccessTokenMapper tokens; // 查询、保存或撤销 user_access_tokens 中的令牌记录。
    private final PasswordEncoder passwordEncoder; // 密码散列与匹配接口，当前由 BCryptPasswordEncoder 实现。
    private final Duration tokenTtl; // 一次登录发出的令牌能有效多久。
    private final String dummyPasswordHash; // 账号不存在时仍用于执行一次密码匹配，下面的 login 会用到。

    public IdentityService(
            UserAccountMapper accounts,
            AccessTokenMapper tokens,
            PasswordEncoder passwordEncoder,
            // Spring 读取配置，未设置时用 PT24H，并转换成 Java 的 Duration（时长）。
            @Value("${app.security.token-ttl:PT24H}") Duration tokenTtl
    ) {
        if (tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("令牌有效期必须为正数");
        }
        this.accounts = accounts;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenTtl = tokenTtl;
        // 服务创建时生成一次备用散列；登录不存在的账号时，也走一次密码匹配计算。
        this.dummyPasswordHash = passwordEncoder.encode(AccessTokens.generate());
    }

    /** 创建普通用户账号：检查重名、散列密码、保存账号，返回不包含密码的身份信息。 */
    @Transactional
    public UserIdentity register(RegisterUserCommand command) {
        // 1. 先查用户名，尽早给出重名错误。它不能代替数据库唯一约束：并发请求可能一起查到“不存在”。
        if (accounts.existsByUsername(command.username())) {
            throw usernameConflict();
        }
        // 2. 把原始密码交给 PasswordEncoder 散列，实体里只保存 passwordHash。
        // 公开注册固定创建 USER；管理员由单独的启动初始化入口创建。
        UserAccount account = new UserAccount(
                command.username(), command.displayName(),
                passwordEncoder.encode(command.password()), AccountRole.USER
        );
        try {
            // 3. 直接调用 Mapper.insert，立即执行 SQL，由数据库检查唯一约束。
            // MyBatis 将数据库生成的 id 回填到对象，再整理成 UserIdentity 返回；事务由外层管理。
            accounts.insert(account);
            return toIdentity(account);
        } catch (DataIntegrityViolationException exception) {
            // 唯一约束也保护两个注册请求同时使用同一用户名的情况。
            throw usernameConflict();
        }
    }

    /** 校验用户名和密码，生成一次登录令牌；数据库保存散列，客户端收到原始令牌。 */
    @Transactional
    public AccessTokenResult login(LoginCommand command) {
        // 1. Optional 表示“可能有账号，也可能没有”，先按整理后的用户名查询。
        Optional<UserAccount> found = accounts.findByUsername(command.username());
        // map 在有账号时取得它的密码散列；orElse 在没有账号时选用备用散列。
        // UserAccount::getPasswordHash 是方法引用，可理解为 account -> account.getPasswordHash()。
        String expectedHash = found.map(UserAccount::getPasswordHash).orElse(dummyPasswordHash);
        // 2. matches 比较原始密码与散列。BCrypt 包含随机盐，不能重新 encode 后用字符串相等来判断。
        // 不存在账号也做一次匹配，减少这条分支与错误密码分支之间的耗时差异。
        boolean matches = passwordEncoder.matches(command.password(), expectedHash);
        if (found.isEmpty() || !matches) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        // 上面已经排除“没账号”与“密码错误”，现在可以安全取出账号。
        UserAccount account = found.orElseThrow();
        // 3. 生成随机原始令牌，客户端后续用它证明自己已登录；这不是密码，也不是账号 id。
        String rawToken = AccessTokens.generate();
        // 4. 保存账号 id、令牌的 SHA-256 散列与过期时间。服务器以后将收到的令牌散列后查询。
        AccessToken token = new AccessToken(
                account.getId(), AccessTokens.hash(rawToken), Instant.now().plus(tokenTtl)
        );
        tokens.insert(token);
        // 5. 原始令牌只在这次登录响应中发给客户端；数据表中保存的是散列。
        // Controller 再将此业务结果转为 LoginResponse，其中包含 tokenType="Bearer"。
        return new AccessTokenResult(rawToken, token.getExpiresAt(), toIdentity(account));
    }

    /** 3B 再读：后续请求提供原始令牌，在数据库中验证它并取得登录身份。 */
    public Optional<UserIdentity> authenticate(String rawToken) {
        if (!AccessTokens.isValidFormat(rawToken)) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(AccessTokens.hash(rawToken))
                .filter(token -> token.isValidAt(Instant.now()))
                .flatMap(token -> Optional.ofNullable(accounts.selectById(token.getUserId())))
                .map(this::toIdentity);
    }

    /** 3B 再读：退出时撤销这一枚令牌；同一账号的其他登录令牌不受影响。 */
    @Transactional
    public void revoke(String rawToken) {
        if (AccessTokens.isValidFormat(rawToken)) {
            tokens.revokeByTokenHash(AccessTokens.hash(rawToken));
        }
    }

    /** 启动初始化使用：创建管理员；已有管理员则保留账号与原密码。 */
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
        UserAccount account = new UserAccount(
                command.username(), command.displayName(),
                passwordEncoder.encode(command.password()), AccountRole.ADMIN
        );
        accounts.insert(account);
        return toIdentity(account);
    }

    // 从持久化实体取出业务需要的身份信息：id、用户名、显示姓名和是否为管理员。
    // 密码散列留在身份模块内部，调用方拿到 UserIdentity 即可做归属、权限检查。
    private UserIdentity toIdentity(UserAccount account) {
        return new UserIdentity(account.getId(), account.getUsername(),
                account.getDisplayName(), account.getRole() == AccountRole.ADMIN);
    }

    private BusinessException usernameConflict() {
        return new BusinessException(ErrorCode.CONFLICT, "用户名已存在");
    }
}
