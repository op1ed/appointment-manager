package com.op1ed.appointmentmanager.appointment.domain;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.op1ed.appointmentmanager.shared.error.BusinessException;
import com.op1ed.appointmentmanager.shared.error.ErrorCode;

@TableName("appointments")
public class Appointment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private Long slotId;

    private String customerName;

    private String doctorName;

    private Instant startTime;

    private Instant endTime;

    private AppointmentStatus status = AppointmentStatus.BOOKED;

    protected Appointment() {
    }

    public Appointment(
            String customerName,
            Long slotId,
            String doctorName,
            Instant startTime,
            Instant endTime
    ) {
        this.customerName = customerName;
        this.slotId = slotId;
        this.doctorName = doctorName;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Appointment(
            Long ownerId,
            String customerName,
            Long slotId,
            String doctorName,
            Instant startTime,
            Instant endTime
    ) {
        this(customerName, slotId, doctorName, startTime, endTime);
        this.ownerId = ownerId;
    }

    public void cancel() {
        this.status = AppointmentStatus.CANCELLED;
    }

    public void rescheduleTo(AppointmentSlot target, String doctorName, Instant now) {
        if (status != AppointmentStatus.BOOKED || slotId == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "该预约不能改期");
        }
        if (!startTime.isAfter(now)) {
            throw new BusinessException(ErrorCode.CONFLICT, "预约已经开始，不能改期");
        }
        if (!target.isInFutureAt(now)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "目标时段已经开始，不能改期");
        }
        this.slotId = target.getId();
        this.doctorName = doctorName;
        this.startTime = target.getStartTime();
        this.endTime = target.getEndTime();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public AppointmentStatus getStatus() {
        return status;
    }
}
