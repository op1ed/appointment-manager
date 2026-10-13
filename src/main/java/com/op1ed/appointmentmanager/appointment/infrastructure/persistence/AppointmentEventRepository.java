package com.op1ed.appointmentmanager.appointment.infrastructure.persistence;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentEventRepository extends JpaRepository<AppointmentEvent, Long> {
    Page<AppointmentEvent> findByAppointmentId(Long appointmentId, Pageable pageable);
}
