package com.op1ed.appointmentmanager.doctor.application.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.op1ed.appointmentmanager.doctor.application.CreateDoctorCommand;
import com.op1ed.appointmentmanager.doctor.application.DoctorInfo;
import com.op1ed.appointmentmanager.doctor.application.DoctorPageResult;
import com.op1ed.appointmentmanager.doctor.application.DoctorService;
import com.op1ed.appointmentmanager.doctor.domain.Doctor;
import com.op1ed.appointmentmanager.doctor.infrastructure.persistence.mapper.DoctorMapper;
import com.op1ed.appointmentmanager.identity.application.UserIdentity;
import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;
import com.op1ed.appointmentmanager.shared.persistence.StoragePage;

/** 医生服务的实现：校验业务输入、调用 Mapper，并由 Spring 管理事务。 */
@Service
@Transactional(readOnly = true)
public class DoctorServiceImpl implements DoctorService {

    private final DoctorMapper doctorMapper;

    public DoctorServiceImpl(DoctorMapper doctorMapper) {
        this.doctorMapper = doctorMapper;
    }

    @Override
    @Transactional
    public DoctorInfo create(UserIdentity caller, CreateDoctorCommand command) {
        UserIdentity.requireAuthenticated(caller).requireAdministrator();
        Doctor doctor = new Doctor(command.name());
        // insert 返回影响行数；数据库生成的 id 会回填到 doctor 本身。
        doctorMapper.insert(doctor);
        return toInfo(doctor);
    }

    @Override
    public List<DoctorInfo> findAll() {
        return doctorMapper
                .listOrdered()
                .stream()
                .map(this::toInfo)
                .toList();
    }

    @Override
    public Optional<DoctorInfo> findById(long id) {
        BusinessInputs.requirePositiveId(id, "医生 ID");
        // BaseMapper 的单条查询可能返回 null，用 Optional 表达“可能不存在”。
        return Optional.ofNullable(doctorMapper.selectById(id)).map(this::toInfo);
    }

    @Override
    public DoctorPageResult search(int page, int size, String name) {
        BusinessInputs.requirePageParameters(page, size);
        String filter = name == null ? "" : BusinessInputs.requireName(name, "医生姓名");
        // 保留“包含字面量”的含义：用户输入的 %、_ 不作为 LIKE 通配符。
        String pattern = "%" + filter.replace("=", "==").replace("%", "=%").replace("_", "=_") + "%";
        var result = StoragePage.from(doctorMapper.search(StoragePage.request(page, size), pattern))
                .map(this::toInfo);
        return new DoctorPageResult(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), (long) result.getNumber() + 1 < result.getTotalPages());
    }

    @Override
    public DoctorInfo requireById(long id) {
        return findById(id).orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND,
                "医生不存在"
        ));
    }

    // 调用方开启写事务；此方法不创建或提前结束事务，医生锁保持到外层提交。
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DoctorInfo lockForScheduling(long id) {
        BusinessInputs.requirePositiveId(id, "医生 ID");
        return doctorMapper.lockById(id)
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
