CREATE TABLE doctors (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE appointment_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    doctor_id BIGINT NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    UNIQUE KEY uk_slots_doctor_start (doctor_id, start_time),

    CONSTRAINT fk_slots_doctor
        FOREIGN KEY (doctor_id)
        REFERENCES doctors (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_slots_time
        CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE appointments
    ADD COLUMN slot_id BIGINT NULL,
    ADD COLUMN end_time DATETIME(6) NULL,

    ADD COLUMN active_slot_id BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'BOOKED' THEN slot_id
                ELSE NULL
            END
        ) STORED,

    ADD UNIQUE KEY uk_appointments_active_slot (active_slot_id),

    ADD KEY idx_appointments_slot_status (slot_id, status),

    ADD CONSTRAINT fk_appointments_slot
        FOREIGN KEY (slot_id)
        REFERENCES appointment_slots (id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT;