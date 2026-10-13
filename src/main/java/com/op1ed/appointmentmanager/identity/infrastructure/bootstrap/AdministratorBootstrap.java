package com.op1ed.appointmentmanager.identity.infrastructure.bootstrap;

import com.op1ed.appointmentmanager.identity.application.IdentityService;
import com.op1ed.appointmentmanager.identity.application.RegisterUserCommand;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdministratorBootstrap implements ApplicationRunner {
    private final IdentityService identityService;
    private final String username;
    private final String password;

    public AdministratorBootstrap(
            IdentityService identityService,
            @Value("${app.security.admin-username:}") String username,
            @Value("${app.security.admin-password:}") String password
    ) {
        this.identityService = identityService;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("管理员初始化需要同时配置 APP_ADMIN_USERNAME 和 APP_ADMIN_PASSWORD");
        }
        identityService.bootstrapAdministrator(new RegisterUserCommand(username, "管理员", password));
    }
}
