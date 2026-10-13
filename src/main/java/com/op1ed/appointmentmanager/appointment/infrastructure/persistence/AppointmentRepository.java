package com.op1ed.appointmentmanager.appointment.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.op1ed.appointmentmanager.appointment.domain.Appointment;
import com.op1ed.appointmentmanager.appointment.domain.AppointmentStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository
        extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByIdAndOwnerId(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id and (:ownerId is null or a.ownerId = :ownerId)")
    Optional<Appointment> findVisibleByIdForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);

    boolean existsBySlotIdAndStatus(
            Long slotId,
            AppointmentStatus status
    );

    List<Appointment> findBySlotIdInAndStatus(
            List<Long> slotIds,
            AppointmentStatus status
    );

    @Query(
            value = """
                    select a from Appointment a
                    where (:ownerId is null or a.ownerId = :ownerId)
                      and (:status is null or a.status = :status)
                      and (:customerName is null or a.customerName = :customerName)
                      and (:fromTime is null or a.startTime >= :fromTime)
                      and (:toTime is null or a.startTime < :toTime)
                    """,
            countQuery = """
                    select count(a) from Appointment a
                    where (:ownerId is null or a.ownerId = :ownerId)
                      and (:status is null or a.status = :status)
                      and (:customerName is null or a.customerName = :customerName)
                      and (:fromTime is null or a.startTime >= :fromTime)
                      and (:toTime is null or a.startTime < :toTime)
                    """
    )
    Page<Appointment> search(
            @Param("ownerId") Long ownerId,
            @Param("status") AppointmentStatus status,
            @Param("customerName") String customerName,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable
    );
}
