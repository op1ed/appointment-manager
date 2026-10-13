CREATE TABLE agent_conversations (
    id VARCHAR(36) NOT NULL,
    owner_id BIGINT NOT NULL,
    revision BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_agent_conversations_owner_updated (owner_id, updated_at),
    CONSTRAINT ck_agent_conversation_revision CHECK (revision >= 0),
    CONSTRAINT fk_agent_conversation_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE agent_conversation_turns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id VARCHAR(36) NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    turn_number BIGINT NOT NULL,
    user_message VARCHAR(2000) NOT NULL,
    messages_json MEDIUMTEXT NOT NULL,
    response_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_turn_request (conversation_id, request_id),
    UNIQUE KEY uk_agent_turn_number (conversation_id, turn_number),
    CONSTRAINT ck_agent_turn_number CHECK (turn_number > 0),
    CONSTRAINT ck_agent_turn_messages CHECK (JSON_VALID(messages_json)),
    CONSTRAINT ck_agent_turn_response CHECK (JSON_VALID(response_json)),
    CONSTRAINT fk_agent_turn_conversation FOREIGN KEY (conversation_id) REFERENCES agent_conversations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
