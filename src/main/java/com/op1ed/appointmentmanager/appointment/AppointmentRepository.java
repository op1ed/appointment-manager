package com.op1ed.appointmentmanager.appointment;

import java.util.List;

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