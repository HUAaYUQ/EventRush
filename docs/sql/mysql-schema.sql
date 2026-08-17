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

CREATE TABLE IF NOT EXISTS ticket_waitlist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    ticket_category_id BIGINT NOT NULL,
    unit_price_cents BIGINT NOT NULL,
    quantity INT NOT NULL,
    active_waitlist_key VARCHAR(96) NULL,
    waitlist_status VARCHAR(32) NOT NULL,
    order_id BIGINT NULL,
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL,
    fulfilled_time DATETIME NULL,
    canceled_time DATETIME NULL,
    expired_time DATETIME NULL,
    payment_expire_time DATETIME NULL,
    UNIQUE KEY uk_ticket_waitlist_active (active_waitlist_key),
    KEY idx_ticket_waitlist_queue (session_id, ticket_category_id, waitlist_status, created_time, id),
    KEY idx_ticket_waitlist_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_waitlist_passenger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    waitlist_id BIGINT NOT NULL,
    passenger_sequence INT NOT NULL,
    passenger_name VARCHAR(64) NOT NULL,
    passenger_document_type VARCHAR(32) NOT NULL,
    passenger_document_last4 VARCHAR(4) NOT NULL,
    UNIQUE KEY uk_ticket_waitlist_passenger_sequence (waitlist_id, passenger_sequence),
    KEY idx_ticket_waitlist_passenger_waitlist (waitlist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    icon_key VARCHAR(40) NOT NULL,
    content_profile VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    display_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL,
    UNIQUE KEY uk_event_category_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organizer_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    city VARCHAR(80) NOT NULL,
    location VARCHAR(160) NOT NULL,
    venue_address VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    poster_url VARCHAR(255) NOT NULL DEFAULT '',
    duration_minutes INT NOT NULL,
    sale_start_time DATETIME NOT NULL,
    sale_end_time DATETIME NOT NULL,
    purchase_limit INT NOT NULL,
    real_name_rule VARCHAR(32) NOT NULL,
    entry_method VARCHAR(32) NOT NULL,
    refund_rule VARCHAR(1000) NOT NULL,
    waitlist_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL,
    published_time DATETIME NULL,
    KEY idx_event_catalog_organizer (organizer_id, updated_time),
    KEY idx_event_catalog_category (category_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_publication (
    event_id BIGINT PRIMARY KEY,
    organizer_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    category_name VARCHAR(40) NOT NULL,
    content_profile VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    name VARCHAR(100) NOT NULL,
    city VARCHAR(80) NOT NULL,
    venue_name VARCHAR(160) NOT NULL,
    venue_address VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    poster_url VARCHAR(255) NOT NULL DEFAULT '',
    duration_minutes INT NOT NULL,
    sale_start_time DATETIME NOT NULL,
    sale_end_time DATETIME NOT NULL,
    purchase_limit INT NOT NULL,
    real_name_rule VARCHAR(32) NOT NULL,
    entry_method VARCHAR(32) NOT NULL,
    refund_rule VARCHAR(1000) NOT NULL,
    waitlist_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    published_time DATETIME NOT NULL,
    KEY idx_event_publication_category (category_id, published_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_session_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL,
    KEY idx_event_session_catalog_event (event_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_session_publication (
    event_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    UNIQUE KEY uk_event_session_publication (event_id, session_id),
    KEY idx_event_session_publication_time (event_id, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_category_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    price_cents BIGINT NOT NULL,
    total_stock INT NOT NULL,
    remaining_stock INT NOT NULL,
    created_time DATETIME NOT NULL,
    updated_time DATETIME NOT NULL,
    KEY idx_ticket_category_catalog_session (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ticket_category_publication (
    event_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    ticket_category_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    price_cents BIGINT NOT NULL,
    total_stock INT NOT NULL,
    UNIQUE KEY uk_ticket_category_publication (event_id, session_id, ticket_category_id),
    KEY idx_ticket_category_publication_session (session_id, ticket_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_rule_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    rule_group VARCHAR(32) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    title VARCHAR(80) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_event_rule_catalog (event_id, rule_code),
    KEY idx_event_rule_catalog_order (event_id, rule_group, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_rule_publication (
    event_id BIGINT NOT NULL,
    rule_group VARCHAR(32) NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    title VARCHAR(80) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_event_rule_publication (event_id, rule_code),
    KEY idx_event_rule_publication_order (event_id, rule_group, display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_detail_section_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    section_type VARCHAR(32) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(5000) NOT NULL,
    image_url VARCHAR(255) NOT NULL DEFAULT '',
    display_order INT NOT NULL DEFAULT 0,
    KEY idx_event_detail_catalog_order (event_id, display_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_detail_section_publication (
    event_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL,
    section_type VARCHAR(32) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(5000) NOT NULL,
    image_url VARCHAR(255) NOT NULL DEFAULT '',
    display_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_event_detail_publication (event_id, section_id),
    KEY idx_event_detail_publication_order (event_id, display_order, section_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
