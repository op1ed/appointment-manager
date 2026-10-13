package com.op1ed.appointmentmanager.identity.application;

public record LoginCommand(String username, String password) {
    public LoginCommand {
        username = IdentityInputs.username(username);
        password = IdentityInputs.password(password);
    }

    @Override
    public String toString() {
        return "LoginCommand[username=" + username + ", password=[REDACTED]]";
    }
}
