package com.op1ed.appointmentmanager.appointment.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "appointment_events")
public class AppointmentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "appointment_id", nullable = false)
    private Long appointmentId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AppointmentAction action;

    @Column(name = "from_slot_id")
    private Long fromSlotId;

    @Column(name = "to_slot_id")
    private Long toSlotId;

    @Column(name = "occurred_at", nullable = false)
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
