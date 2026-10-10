package com.op1ed.appointmentmanager.appointment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentSlotRepository
        extends JpaRepository<AppointmentSlot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppointmentSlot s where s.id = :id")
    Optional<AppointmentSlot> findByIdForUpdate(@Param("id") Long id);

    List<AppointmentSlot> findByDoctorIdOrderByStartTimeAsc(Long doctorId);

    boolean existsByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThan(
            Long doctorId,
            Instant endTime,
            Instant startTime
    );
}
