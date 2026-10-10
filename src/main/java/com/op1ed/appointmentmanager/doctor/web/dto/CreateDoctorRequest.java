package com.op1ed.appointmentmanager.doctor.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDoctorRequest(
        @NotBlank
        @Size(max = 100)
        String name
) {
}
