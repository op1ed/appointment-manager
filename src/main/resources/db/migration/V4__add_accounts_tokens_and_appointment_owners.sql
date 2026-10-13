CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_accounts_username (username),
    CONSTRAINT ck_accounts_role CHECK (role IN ('USER', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_access_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_access_tokens_hash (token_hash),
    KEY idx_access_tokens_user (user_id),
    KEY idx_access_tokens_expiry (expires_at),
    CONSTRAINT fk_access_tokens_user FOREIGN KEY (user_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE appointments
    ADD COLUMN owner_id BIGINT NULL,
    ADD KEY idx_appointments_owner_id (owner_id, id),
    ADD CONSTRAINT fk_appointments_owner FOREIGN KEY (owner_id)
        REFERENCES user_accounts (id) ON DELETE RESTRICT ON UPDATE RESTRICT;
