package com.op1ed.appointmentmanager.appointment.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("appointment_events")
public class AppointmentEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appointmentId;

    private Long actorId;

    private AppointmentAction action;

    private Long fromSlotId;

    private Long toSlotId;

    private Instant occurredAt;

    protected AppointmentEvent() {
    }

    public AppointmentEvent(Long appointmentId, Long actorId, AppointmentAction action,
            Long fromSlotId, Long toSlotId, Instant occurredAt) {
        this.appointmentId = appointmentId;
        this.actorId = actorId;
        this.action = action;
        this.fromSlotId = fromSlotId;
        this.toSlotId = toSlotId;
        this.occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() { return id; }
    public Long getAppointmentId() { return appointmentId; }
    public Long getActorId() { return actorId; }
    public AppointmentAction getAction() { return action; }
    public Long getFromSlotId() { return fromSlotId; }
    public Long getToSlotId() { return toSlotId; }
    public Instant getOccurredAt() { return occurredAt; }
}
