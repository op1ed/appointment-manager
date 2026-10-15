package com.op1ed.appointmentmanager.identity.application;

import java.util.Optional;

/**
 * 身份模块的业务接口：声明注册、登录、令牌验证与管理员初始化能力。
 *
 * <p>具体密码处理、Mapper 调用和事务放在 impl/IdentityServiceImpl 中。
 * 阅读顺序是先看接口了解输入输出，再跟进实现中的 register 和 login。
 */
public interface IdentityService {

    /** 注册普通用户，返回不包含密码散列的身份信息。 */
    UserIdentity register(RegisterUserCommand command);

    /** 校验账号密码并发出访问令牌。 */
    AccessTokenResult login(LoginCommand command);

    /** 验证原始令牌，格式错误、过期或已撤销时返回空 Optional。 */
    Optional<UserIdentity> authenticate(String rawToken);

    /** 撤销当前令牌，不影响同一账号的其他登录。 */
    void revoke(String rawToken);

    /** 启动时创建管理员；已有管理员则保留账号与原密码。 */
    UserIdentity bootstrapAdministrator(RegisterUserCommand command);
}
