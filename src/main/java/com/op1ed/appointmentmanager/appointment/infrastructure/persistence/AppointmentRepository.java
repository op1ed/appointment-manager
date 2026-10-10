package com.op1ed.appointmentmanager.appointment.infrastructure.persistence;

import java.util.List;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository
        extends JpaRepository<Appointment, Long> {

    boolean existsBySlotIdAndStatus(
            Long slotId,
            AppointmentStatus status
    );

    List<Appointment> findBySlotIdInAndStatus(
            List<Long> slotIds,
            AppointmentStatus status
    );
}
