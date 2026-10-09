package com.op1ed.appointmentmanager.appointment;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    @GetMapping
    public List<AppointmentResponse> listAppointments() {
        return List.of();
    }
}