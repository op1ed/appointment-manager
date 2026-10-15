package com.op1ed.appointmentmanager.appointment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentSlotMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;
import com.op1ed.appointmentmanager.shared.persistence.StoragePage;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class AppointmentSlotRepository extends MybatisRepository<AppointmentSlot> {

    private final AppointmentSlotMapper mapper;

    public AppointmentSlotRepository(AppointmentSlotMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<AppointmentSlot> findByIdForUpdate(Long id) {
        return mapper.lockById(id);
    }

    public List<AppointmentSlot> findByDoctorIdOrderByStartTimeAsc(Long doctorId) {
        return mapper.listByDoctor(doctorId);
    }

    public StoragePage<AppointmentSlot> searchAvailable(Long doctorId, Instant now,
            Instant fromTime, Instant toTime, int page, int size) {
        return StoragePage.from(mapper.searchAvailable(StoragePage.request(page, size), doctorId, now, fromTime, toTime));
    }

    public boolean existsByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThan(Long doctorId,
            Instant endTime, Instant startTime) {
        return mapper.overlaps(doctorId, endTime, startTime);
    }
}
