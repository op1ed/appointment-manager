package com.op1ed.appointmentmanager.appointment.infrastructure.persistence;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentEvent;
import com.op1ed.appointmentmanager.appointment.infrastructure.persistence.mapper.AppointmentEventMapper;
import com.op1ed.appointmentmanager.shared.persistence.MybatisRepository;
import com.op1ed.appointmentmanager.shared.persistence.StoragePage;

import org.springframework.stereotype.Repository;

/** 本模块的数据访问入口；Mapper 使用 MyBatis-Plus 执行 SQL。 */
@Repository
public class AppointmentEventRepository extends MybatisRepository<AppointmentEvent> {

    private final AppointmentEventMapper mapper;

    public AppointmentEventRepository(AppointmentEventMapper mapper) {
        super(mapper);
        this.mapper = mapper;
    }

    public StoragePage<AppointmentEvent> findByAppointmentId(Long appointmentId, int page, int size) {
        return StoragePage.from(mapper.history(StoragePage.request(page, size), appointmentId));
    }
}
