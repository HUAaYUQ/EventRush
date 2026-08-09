CREATE DATABASE IF NOT EXISTS eventrush DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE eventrush;

CREATE TABLE IF NOT EXISTS ticket_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    ticket_category_id BIGINT NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    created_time DATETIME NOT NULL,
    pay_time DATETIME NULL,
    cancel_time DATETIME NULL,
    expire_time DATETIME NOT NULL,
    UNIQUE KEY uk_ticket_order_once (user_id, session_id, ticket_category_id),
    KEY idx_ticket_order_user (user_id),
    KEY idx_ticket_order_status_expire (order_status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS electronic_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    ticket_code VARCHAR(64) NOT NULL,
    ticket_status VARCHAR(32) NOT NULL,
    generated_time DATETIME NOT NULL,
    verified_time DATETIME NULL,
    verifier_id BIGINT NULL,
    UNIQUE KEY uk_electronic_ticket_order (order_id),
    UNIQUE KEY uk_electronic_ticket_code (ticket_code),
    KEY idx_electronic_ticket_status (ticket_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
