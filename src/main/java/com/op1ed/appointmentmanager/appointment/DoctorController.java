package com.op1ed.appointmentmanager.appointment;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
        DoctorResponse doctor = doctorService.create(request);
        URI location = URI.create("/api/doctors/" + doctor.id());
        return ResponseEntity.created(location).body(doctor);
    }

    @GetMapping
    public List<DoctorResponse> listDoctors() {
        return doctorService.findAll();
    }

    @GetMapping("/{id}")
    public DoctorResponse getDoctor(@PathVariable("id") long id) {
        return doctorService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "医生不存在"
                ));
    }
}