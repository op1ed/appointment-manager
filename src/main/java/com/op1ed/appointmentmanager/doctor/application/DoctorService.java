package com.op1ed.appointmentmanager.doctor.application;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.op1ed.appointmentmanager.doctor.domain.Doctor;
import com.op1ed.appointmentmanager.doctor.infrastructure.persistence.DoctorRepository;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

@Service
@Transactional(readOnly = true)
public class DoctorService {

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @Transactional
    public DoctorInfo create(CreateDoctorCommand command) {
        Doctor doctor = new Doctor(command.name());
        return toInfo(doctorRepository.save(doctor));
    }

    public List<DoctorInfo> findAll() {
        return doctorRepository
                .findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toInfo)
                .toList();
    }

    public Optional<DoctorInfo> findById(long id) {
        BusinessInputs.requirePositiveId(id, "医生 ID");
        return doctorRepository.findById(id).map(this::toInfo);
    }

    public DoctorInfo requireById(long id) {
        return findById(id).orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND,
                "医生不存在"
        ));
    }

    // 调用方开启写事务；此方法不创建或提前结束事务，医生锁保持到外层提交。
    @Transactional(propagation = Propagation.MANDATORY)
    public DoctorInfo lockForScheduling(long id) {
        BusinessInputs.requirePositiveId(id, "医生 ID");
        return doctorRepository.findByIdForUpdate(id)
                .map(this::toInfo)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND,
                        "医生不存在"
                ));
    }

    private DoctorInfo toInfo(Doctor doctor) {
        return new DoctorInfo(doctor.getId(), doctor.getName());
    }
}
