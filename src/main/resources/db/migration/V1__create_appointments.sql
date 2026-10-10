CREATE TABLE appointments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_name VARCHAR(100) NOT NULL,
    doctor_name VARCHAR(100) NOT NULL,
    start_time DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;