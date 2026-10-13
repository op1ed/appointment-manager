package com.op1ed.appointmentmanager.identity.web.dto;

import com.op1ed.appointmentmanager.identity.application.RegisterUserCommand;

public record RegisterRequest(String username, String displayName, String password) {
    public RegisterUserCommand toCommand() {
        return new RegisterUserCommand(username, displayName, password);
    }

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", password=[REDACTED]]";
    }
}
