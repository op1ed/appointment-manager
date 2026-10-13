CREATE TABLE agent_actions (
    id VARCHAR(36) NOT NULL,
    owner_id BIGINT NOT NULL,
    kind VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    appointment_id BIGINT NULL,
    customer_name VARCHAR(100) NULL,
    expected_slot_id BIGINT NULL,
    target_slot_id BIGINT NULL,
    preview_json MEDIUMTEXT NOT NULL,
    result_json MEDIUMTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_agent_actions_owner_created (owner_id, created_at),
    CONSTRAINT ck_agent_action_kind CHECK (kind IN ('BOOK', 'CANCEL', 'RESCHEDULE')),
    CONSTRAINT ck_agent_action_status CHECK (status IN ('PENDING', 'CONFIRMED')),
    CONSTRAINT ck_agent_action_preview CHECK (JSON_VALID(preview_json)),
    CONSTRAINT ck_agent_action_result CHECK (result_json IS NULL OR JSON_VALID(result_json)),
    CONSTRAINT ck_agent_action_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_agent_action_confirmation CHECK (
        (status = 'PENDING' AND result_json IS NULL AND confirmed_at IS NULL) OR
        (status = 'CONFIRMED' AND result_json IS NOT NULL AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT ck_agent_action_inputs CHECK (
        (kind = 'BOOK' AND appointment_id IS NULL AND customer_name IS NOT NULL
            AND expected_slot_id IS NULL AND target_slot_id IS NOT NULL) OR
        (kind = 'CANCEL' AND appointment_id IS NOT NULL AND customer_name IS NULL AND target_slot_id IS NULL) OR
        (kind = 'RESCHEDULE' AND appointment_id IS NOT NULL AND customer_name IS NULL
            AND expected_slot_id IS NOT NULL AND target_slot_id IS NOT NULL)
    ),
    CONSTRAINT fk_agent_action_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_agent_action_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_agent_action_expected_slot FOREIGN KEY (expected_slot_id) REFERENCES appointment_slots (id),
    CONSTRAINT fk_agent_action_target_slot FOREIGN KEY (target_slot_id) REFERENCES appointment_slots (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
