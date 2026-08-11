CREATE TABLE notification_preferences (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username   VARCHAR(255) NOT NULL,
    channel    VARCHAR(16)  NOT NULL,
    enabled    BOOLEAN      NOT NULL,
    updated_at TIMESTAMP    NOT NULL,
    CONSTRAINT uq_notification_preferences_username_channel UNIQUE (username, channel)
);

CREATE TABLE notifications (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    recipient_username  VARCHAR(255) NOT NULL,
    channel             VARCHAR(16)  NOT NULL,
    template_key        VARCHAR(32)  NOT NULL,
    message             VARCHAR(1000) NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    retry_count         INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    sent_at             TIMESTAMP
);

CREATE INDEX idx_notifications_status ON notifications (status);
CREATE INDEX idx_notifications_recipient_username ON notifications (recipient_username);
