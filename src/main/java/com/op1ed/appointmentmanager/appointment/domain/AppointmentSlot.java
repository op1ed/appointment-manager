package com.op1ed.appointmentmanager.appointment.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.BusinessInputs;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

@TableName("appointment_slots")
public class AppointmentSlot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long doctorId;

    private Instant startTime;

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
