package com.op1ed.appointmentmanager.doctor.application;

import com.op1ed.appointmentmanager.shared.error.BusinessInputs;

public record CreateDoctorCommand(String name) {

    public CreateDoctorCommand {
        name = BusinessInputs.requireName(name, "医生姓名");
    }
}
