-- 账号与请求 ID 共同标识一次创建意图，两个账号可以使用同一个 UUID。
CREATE TABLE appointment_booking_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    request_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    slot_id BIGINT NOT NULL,
    response_json TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_booking_request_owner (owner_id, request_id),
    CONSTRAINT ck_booking_request_response CHECK (response_json IS NULL OR JSON_VALID(response_json)),
    CONSTRAINT fk_booking_request_owner FOREIGN KEY (owner_id) REFERENCES user_accounts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- slot_id 是请求输入，先记录请求，再由业务服务校验并锁定时段。
-- 不在这里增加时段外键，以免插入时提前获取时段共享锁，打乱显式加锁顺序。
