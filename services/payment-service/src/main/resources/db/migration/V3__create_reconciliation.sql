-- Reconciliation: our payments and refunds checked against the provider's settlement report.
-- A run records what was checked; a finding is one mismatch, kept open until an admin re-syncs or
-- resolves it, or a later run sees the records agree again.
CREATE TABLE reconciliation_runs (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    window_from       TIMESTAMP     NOT NULL,
    window_to         TIMESTAMP     NOT NULL,
    started_at        TIMESTAMP     NOT NULL,
    finished_at       TIMESTAMP,
    status            VARCHAR(16)   NOT NULL,
    transactions      INTEGER       NOT NULL DEFAULT 0,
    payments_checked  INTEGER       NOT NULL DEFAULT 0,
    findings_opened   INTEGER       NOT NULL DEFAULT 0,
    findings_cleared  INTEGER       NOT NULL DEFAULT 0,
    error             VARCHAR(1024)
);

CREATE TABLE reconciliation_findings (
    id                         BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id                     BIGINT         NOT NULL REFERENCES reconciliation_runs (id),
    type                       VARCHAR(32)    NOT NULL,
    status                     VARCHAR(16)    NOT NULL,
    resolution                 VARCHAR(16),
    note                       VARCHAR(1024),
    resolved_by                VARCHAR(255),
    payment_id                 BIGINT,
    order_id                   BIGINT,
    checkout_session_id        VARCHAR(255)   NOT NULL,
    our_status                 VARCHAR(16),
    our_amount                 NUMERIC(19, 2),
    our_currency               VARCHAR(8),
    provider_status            VARCHAR(16),
    provider_amount            NUMERIC(19, 2),
    provider_currency          VARCHAR(8),
    provider_charge_reference  VARCHAR(255),
    provider_refund_reference  VARCHAR(255),
    detail                     VARCHAR(1024),
    first_seen_at              TIMESTAMP      NOT NULL,
    last_seen_at               TIMESTAMP      NOT NULL,
    resolved_at                TIMESTAMP
);

-- One open finding per mismatch: repeated runs update it instead of piling up duplicates.
CREATE UNIQUE INDEX uq_reconciliation_findings_open
    ON reconciliation_findings (type, checkout_session_id) WHERE status = 'OPEN';
CREATE INDEX idx_reconciliation_findings_status ON reconciliation_findings (status);
