package com.op1ed.appointmentmanager.appointment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.appointment.domain.AppointmentSlot;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentSlotRepository
        extends JpaRepository<AppointmentSlot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AppointmentSlot s where s.id = :id")
    Optional<AppointmentSlot> findByIdForUpdate(@Param("id") Long id);

    List<AppointmentSlot> findByDoctorIdOrderByStartTimeAsc(Long doctorId);

    @Query(value = """
            select s from AppointmentSlot s
            where s.doctorId = :doctorId and s.startTime > :now
              and (:fromTime is null or s.startTime >= :fromTime)
              and (:toTime is null or s.startTime < :toTime)
              and not exists (select a.id from Appointment a
                  where a.slotId = s.id and a.status = com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus.BOOKED)
            """, countQuery = """
            select count(s) from AppointmentSlot s
            where s.doctorId = :doctorId and s.startTime > :now
              and (:fromTime is null or s.startTime >= :fromTime)
              and (:toTime is null or s.startTime < :toTime)
              and not exists (select a.id from Appointment a
                  where a.slotId = s.id and a.status = com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus.BOOKED)
            """)
    Page<AppointmentSlot> searchAvailable(@Param("doctorId") Long doctorId, @Param("now") Instant now,
            @Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime, Pageable pageable);

    boolean existsByDoctorIdAndStartTimeLessThanAndEndTimeGreaterThan(
            Long doctorId,
            Instant endTime,
            Instant startTime
    );
}
