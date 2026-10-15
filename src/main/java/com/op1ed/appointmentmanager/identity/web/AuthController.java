package com.op1ed.appointmentmanager.identity.web;

import com.op1ed.appointmentmanager.identity.application.IdentityService;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.identity.web.dto.LoginRequest;
import com.op1ed.appointmentmanager.identity.web.dto.LoginResponse;
import com.op1ed.appointmentmanager.identity.web.dto.RegisterRequest;
import com.op1ed.appointmentmanager.identity.web.dto.UserResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身份模块阅读 3A：从 register、login 两个请求入口开始。
 *
 * <p>与已经读过的 DoctorController 一样，这里接收 HTTP 请求，调用应用服务并转换响应。
 * 注册请求建立账号；登录请求验证账号密码、返回后续请求使用的令牌。
 *
 * <p>阅读顺序：本类的 register/login → 对应 Request.toCommand() → Command 构造校验
 * → IdentityService 的同名方法 → Response.from()。校验在 Command 构造时执行，
 * 应用服务的其他调用入口也能复用这些输入规则。
 * me、logout 及请求携带令牌的过程留到 3B 再展开。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final IdentityService identityService;

    // 构造器注入：Spring 创建 Controller 时，传入已经组装好的 IdentityService。
    public AuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    /**
     * POST /api/auth/register：创建普通账号，返回 201 和不含密码的用户资料。
     * 注册成功后还需要调用 login 取得令牌，本接口只完成注册。
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        // @RequestBody 将 JSON 读成 RegisterRequest；toCommand 转换输入并触发业务参数校验。
        // register 返回 UserIdentity，UserResponse.from 再整理成 HTTP 响应字段。
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(identityService.register(request.toCommand())));
    }

    /** POST /api/auth/login：校验账号密码，返回令牌、过期时间与用户资料，默认状态为 200。 */
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        // 顺着三个调用读：先创建 LoginCommand，再执行登录，最后转换成 LoginResponse。
        return LoginResponse.from(identityService.login(request.toCommand()));
    }

    // 3B 再读：过滤器已验证令牌后，@AuthenticationPrincipal 取得当前请求的登录身份。
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserIdentity user) {
        return UserResponse.from(UserIdentity.requireAuthenticated(user));
    }

    // 3B 再读：退出撤销本次请求携带的令牌，返回 204（响应没有正文）。
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        identityService.revoke(authorization.substring(7).strip());
        return ResponseEntity.noContent().build();
    }
}
