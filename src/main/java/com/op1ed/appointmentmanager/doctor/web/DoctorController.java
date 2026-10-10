package com.op1ed.appointmentmanager.doctor.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.op1ed.appointmentmanager.doctor.application.CreateDoctorCommand;
import com.op1ed.appointmentmanager.doctor.application.DoctorInfo;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.doctor.web.dto.CreateDoctorRequest;
import com.op1ed.appointmentmanager.doctor.web.dto.DoctorResponse;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @PostMapping
    public ResponseEntity<DoctorResponse> createDoctor(
            @Valid @RequestBody CreateDoctorRequest request
    ) {
        DoctorInfo doctor = doctorService.create(new CreateDoctorCommand(request.name()));
        URI location = URI.create("/api/doctors/" + doctor.id());
        return ResponseEntity.created(location).body(toResponse(doctor));
    }

    @GetMapping
    public List<DoctorResponse> listDoctors() {
        return doctorService.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public DoctorResponse getDoctor(@PathVariable("id") long id) {
        return toResponse(doctorService.requireById(id));
    }

    private DoctorResponse toResponse(DoctorInfo doctor) {
        return new DoctorResponse(doctor.id(), doctor.name());
    }
}
