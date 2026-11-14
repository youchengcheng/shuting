-- Review artifact only. application.properties disables automatic SQL initialization.
-- Notification DB: user_state, notification and event_error. Source DBs: outbox and event_error.
-- If all services share one schema, apply the four statements once after review.
CREATE TABLE IF NOT EXISTS t_notification_user_state (
    recipient_id BIGINT NOT NULL,
    last_seq BIGINT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    like_collection_read_seq BIGINT NOT NULL DEFAULT 0,
    comment_reply_read_seq BIGINT NOT NULL DEFAULT 0,
    follow_read_seq BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_notification (
    id BIGINT NOT NULL AUTO_INCREMENT,
    business_key VARCHAR(191) NOT NULL,
    recipient_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    type VARCHAR(24) NOT NULL,
    category VARCHAR(32) NOT NULL,
    recipient_role VARCHAR(24) NOT NULL,
    note_id BIGINT NULL,
    comment_id BIGINT NULL,
    reply_comment_id BIGINT NULL,
    occurred_at DATETIME NOT NULL,
    seq BIGINT NOT NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_business_recipient (business_key,recipient_id),
    UNIQUE KEY uk_notification_recipient_seq (recipient_id,seq),
    KEY idx_notification_category_seq (recipient_id,category,seq),
    KEY idx_notification_unread (recipient_id,category,read_at,seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_notification_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    producer_service VARCHAR(64) NOT NULL,
    business_key VARCHAR(191) NOT NULL,
    event_id CHAR(36) NOT NULL,
    payload LONGTEXT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    next_attempt_at DATETIME NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(64) NULL,
    lease_version BIGINT NOT NULL DEFAULT 0,
    lease_until DATETIME NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME NOT NULL,
    sent_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_outbox_business (producer_service,business_key),
    UNIQUE KEY uk_notification_outbox_event (event_id),
    KEY idx_notification_outbox_scan (producer_service,state,next_attempt_at,lease_until,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS t_notification_event_error (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(64) NOT NULL,
    event_identity VARCHAR(64) NOT NULL,
    payload LONGTEXT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_error (source,event_identity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
