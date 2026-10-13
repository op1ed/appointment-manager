package com.op1ed.appointmentmanager.identity.web.dto;

import com.op1ed.appointmentmanager.identity.application.LoginCommand;

public record LoginRequest(String username, String password) {
    public LoginCommand toCommand() {
        return new LoginCommand(username, password);
    }

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=[REDACTED]]";
    }
}
