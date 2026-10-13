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

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final IdentityService identityService;

    public AuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(identityService.register(request.toCommand())));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return LoginResponse.from(identityService.login(request.toCommand()));
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserIdentity user) {
        return UserResponse.from(UserIdentity.requireAuthenticated(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        identityService.revoke(authorization.substring(7).strip());
        return ResponseEntity.noContent().build();
    }
}
