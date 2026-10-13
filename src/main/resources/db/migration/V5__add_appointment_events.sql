CREATE TABLE appointment_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    appointment_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    from_slot_id BIGINT NULL,
    to_slot_id BIGINT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_events_appointment_id (appointment_id, id),
    CONSTRAINT ck_events_action CHECK (action IN ('BOOKED', 'RESCHEDULED', 'CANCELLED')),
    CONSTRAINT fk_events_appointment FOREIGN KEY (appointment_id)
        REFERENCES appointments (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_events_actor FOREIGN KEY (actor_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_events_from_slot FOREIGN KEY (from_slot_id)
        REFERENCES appointment_slots (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_events_to_slot FOREIGN KEY (to_slot_id)
        REFERENCES appointment_slots (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
