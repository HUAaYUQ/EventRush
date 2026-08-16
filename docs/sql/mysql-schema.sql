CREATE DATABASE IF NOT EXISTS eventrush DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE eventrush;

CREATE TABLE IF NOT EXISTS ticket_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    ticket_category_id BIGINT NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    amount_cents BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    refunded_quantity INT NOT NULL DEFAULT 0,
    refunded_amount_cents BIGINT NOT NULL DEFAULT 0,
    passenger_name VARCHAR(64) NOT NULL,
    passenger_document_type VARCHAR(32) NOT NULL,
    passenger_document_last4 VARCHAR(4) NOT NULL,
    active_grab_key VARCHAR(96) NULL,
    order_status VARCHAR(32) NOT NULL,
    created_time DATETIME NOT NULL,
    pay_time DATETIME NULL,
    cancel_time DATETIME NULL,
    refund_time DATETIME NULL,
    expire_time DATETIME NOT NULL,
    UNIQUE KEY uk_ticket_order_active (active_grab_key),
    KEY idx_ticket_order_user (user_id),
    KEY idx_ticket_order_status_expire (order_status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_order_passenger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    passenger_sequence INT NOT NULL,
    passenger_name VARCHAR(64) NOT NULL,
    passenger_document_type VARCHAR(32) NOT NULL,
    passenger_document_last4 VARCHAR(4) NOT NULL,
    UNIQUE KEY uk_ticket_order_passenger_sequence (order_id, passenger_sequence),
    KEY idx_ticket_order_passenger_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS electronic_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    passenger_id BIGINT NOT NULL,
    ticket_code VARCHAR(64) NOT NULL,
    ticket_status VARCHAR(32) NOT NULL,
    generated_time DATETIME NOT NULL,
    verified_time DATETIME NULL,
    verifier_id BIGINT NULL,
    refunded_time DATETIME NULL,
    UNIQUE KEY uk_electronic_ticket_passenger (passenger_id),
    UNIQUE KEY uk_electronic_ticket_code (ticket_code),
    KEY idx_electronic_ticket_status (ticket_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS async_grab_request (
    request_id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    ticket_category_id BIGINT NOT NULL,
    request_status VARCHAR(32) NOT NULL,
    order_id BIGINT NULL,
    error_message VARCHAR(255) NULL,
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL,
    KEY idx_async_grab_status (request_status, updated_time),
    KEY idx_async_grab_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
