package com.op1ed.appointmentmanager.appointment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @Transactional
    public DoctorResponse create(CreateDoctorRequest request) {
        Doctor doctor = new Doctor(request.name().strip());
        return toResponse(doctorRepository.save(doctor));
    }

    public List<DoctorResponse> findAll() {
        return doctorRepository
                .findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<DoctorResponse> findById(long id) {
        return doctorRepository.findById(id)
                .map(this::toResponse);
    }

    private DoctorResponse toResponse(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getName()
        );
    }
}