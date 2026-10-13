CREATE TABLE agent_rescheduling_workflows (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    appointment_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    next_node VARCHAR(80) NOT NULL,
    state_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_workflow_owner_updated (owner_id, updated_at),
    CONSTRAINT ck_workflow_revision CHECK (revision >= 0),
    CONSTRAINT ck_workflow_state CHECK (JSON_VALID(state_json)),
    CONSTRAINT ck_workflow_status CHECK (status IN ('CREATED', 'WAITING_FOR_SELECTION', 'NO_CANDIDATES', 'WAITING_FOR_CONFIRMATION', 'COMPLETED', 'REJECTED', 'ABORTED')),
    CONSTRAINT fk_workflow_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_workflow_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_workflow_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    workflow_id VARCHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    payload_json TEXT NOT NULL,
    response_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_workflow_request_owner (owner_id, request_id),
    CONSTRAINT ck_workflow_request_payload CHECK (JSON_VALID(payload_json)),
    CONSTRAINT ck_workflow_request_response CHECK (JSON_VALID(response_json)),
    CONSTRAINT fk_workflow_request_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_workflow_request_workflow FOREIGN KEY (workflow_id) REFERENCES agent_rescheduling_workflows (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_workflow_checkpoints (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    checkpoint_id VARCHAR(36) NOT NULL,
    workflow_id VARCHAR(36) NOT NULL,
    node_id VARCHAR(80) NOT NULL,
    next_node_id VARCHAR(80) NOT NULL,
    state_json MEDIUMTEXT NOT NULL,
    UNIQUE KEY uk_workflow_checkpoint (workflow_id, checkpoint_id),
    CONSTRAINT ck_workflow_checkpoint_state CHECK (JSON_VALID(state_json)),
    CONSTRAINT fk_workflow_checkpoint_workflow FOREIGN KEY (workflow_id) REFERENCES agent_rescheduling_workflows (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE agent_actions ADD COLUMN workflow_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_agent_action_workflow FOREIGN KEY (workflow_id) REFERENCES agent_rescheduling_workflows (id),
    DROP CHECK ck_agent_action_status,
    DROP CHECK ck_agent_action_confirmation,
    ADD CONSTRAINT ck_agent_action_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REVOKED')),
    ADD CONSTRAINT ck_agent_action_confirmation CHECK (
        (status IN ('PENDING', 'REVOKED') AND result_json IS NULL AND confirmed_at IS NULL) OR
        (status = 'CONFIRMED' AND result_json IS NOT NULL AND confirmed_at IS NOT NULL)
    );
