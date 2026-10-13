package com.op1ed.appointmentmanager.identity.application;

import com.op1ed.appointmentmanager.shared.error.BusinessInputs;

public record RegisterUserCommand(String username, String displayName, String password) {
    public RegisterUserCommand {
        username = IdentityInputs.username(username);
        displayName = BusinessInputs.requireName(displayName, "显示姓名");
        password = IdentityInputs.password(password);
    }

    @Override
    public String toString() {
        return "RegisterUserCommand[username=" + username + ", password=[REDACTED]]";
    }
}
