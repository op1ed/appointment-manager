package com.op1ed.appointmentmanager.appointment.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointment_slots")
public class AppointmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    protected AppointmentSlot() {
    }

    public AppointmentSlot(
            Long doctorId,
            Instant startTime,
            Instant endTime
    ) {
        this.doctorId = BusinessInputs.requirePositiveId(doctorId, "医生ID");
        if (startTime == null || endTime == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "时段开始和结束时间不能为空"
            );
        }

        this.startTime = startTime.truncatedTo(ChronoUnit.MICROS);
        this.endTime = endTime.truncatedTo(ChronoUnit.MICROS);
        if (!this.endTime.isAfter(this.startTime)) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "结束时间必须晚于开始时间"
            );
        }
    }

    public boolean isInFutureAt(Instant now) {
        return startTime.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }
}
