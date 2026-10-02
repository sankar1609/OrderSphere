-- Compensating actions (payment refunds, inventory releases) the saga owes other services. Written
-- in the same transaction as the order change that requires them, so they can't be lost; attempted
-- right after commit and retried with back-off by the saga sweep until done (or given up on).
CREATE TABLE order_compensations (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id         BIGINT        NOT NULL,
    type             VARCHAR(32)   NOT NULL,
    payment_id       BIGINT,
    reason           VARCHAR(255),
    status           VARCHAR(16)   NOT NULL,
    attempts         INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMP     NOT NULL,
    last_error       VARCHAR(1024),
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL
);

CREATE INDEX idx_order_compensations_due ON order_compensations (status, next_attempt_at);
