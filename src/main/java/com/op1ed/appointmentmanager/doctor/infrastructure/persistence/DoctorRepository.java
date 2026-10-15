package com.op1ed.appointmentmanager.doctor.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.doctor.domain.Doctor;
import com.op1ed.appointmentmanager.doctor.infrastructure.persistence.mapper.DoctorMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;
import com.op1ed.appointmentmanager.shared.persistence.StoragePage;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class DoctorRepository extends MybatisRepository<Doctor> {

    private final DoctorMapper mapper;

    public DoctorRepository(DoctorMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public List<Doctor> findAll() {
        return mapper.listOrdered();
    }

    public StoragePage<Doctor> findByNameContaining(String name, int page, int size) {
        // 保留原来“包含字面量”的含义：用户输入的 %、_ 不作为 LIKE 通配符。
        String pattern = "%" + name.replace("=", "==").replace("%", "=%").replace("_", "=_") + "%";
        return StoragePage.from(mapper.search(StoragePage.request(page, size), pattern));
    }

    public Optional<Doctor> findByIdForUpdate(Long id) {
        return mapper.lockById(id);
    }
}
