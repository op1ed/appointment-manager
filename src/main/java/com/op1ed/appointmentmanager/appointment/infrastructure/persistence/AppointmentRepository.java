package com.op1ed.appointmentmanager.appointment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;
import com.op1ed.appointmentmanager.shared.persistence.StoragePage;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class AppointmentRepository extends MybatisRepository<Appointment> {

    private final AppointmentMapper mapper;

    public AppointmentRepository(AppointmentMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public Optional<Appointment> findByIdAndOwnerId(Long id, Long ownerId) {
        return mapper.findOwned(id, ownerId);
    }

    public Optional<Appointment> findVisibleByIdForUpdate(Long id, Long ownerId) {
        return mapper.lockVisible(id, ownerId);
    }

    public boolean existsBySlotIdAndStatus(Long slotId, AppointmentStatus status) {
        return mapper.existsOccupied(slotId, status);
    }

    public List<Appointment> findBySlotIdInAndStatus(List<Long> slotIds, AppointmentStatus status) {
        return slotIds.isEmpty() ? List.of() : mapper.findBySlots(slotIds, status);
    }

    public StoragePage<Appointment> search(Long ownerId, AppointmentStatus status, String customerName,
            Instant fromTime, Instant toTime, int page, int size) {
        return StoragePage.from(mapper.search(StoragePage.request(page, size), ownerId, status, customerName, fromTime, toTime));
    }
}
