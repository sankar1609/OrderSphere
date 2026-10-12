-- The audit log: one row per domain event, chained by hash (each row's hash covers the previous
-- row's), so an edit, a deletion or a reordering is detectable by recomputing the chain.

CREATE TABLE audit_entries (
    seq              BIGINT       PRIMARY KEY,
    event_id         VARCHAR(100) NOT NULL UNIQUE,
    event_type       VARCHAR(100) NOT NULL,
    routing_key      VARCHAR(150),
    source_service   VARCHAR(100),
    actor            VARCHAR(100),
    order_id         BIGINT,
    subject_username VARCHAR(100),
    trace_id         VARCHAR(64),
    occurred_at      TIMESTAMPTZ  NOT NULL,
    recorded_at      TIMESTAMPTZ  NOT NULL,
    payload          TEXT         NOT NULL,
    prev_hash        VARCHAR(64)  NOT NULL,
    hash             VARCHAR(64)  NOT NULL
);

CREATE INDEX idx_audit_entries_order_id ON audit_entries (order_id);
CREATE INDEX idx_audit_entries_event_type ON audit_entries (event_type);
CREATE INDEX idx_audit_entries_actor ON audit_entries (actor);
CREATE INDEX idx_audit_entries_subject_username ON audit_entries (subject_username);
CREATE INDEX idx_audit_entries_occurred_at ON audit_entries (occurred_at);

-- Where the chain currently ends. One row, locked for every append, so appends from several
-- instances still form one linear chain.
CREATE TABLE audit_chain_head (
    id   SMALLINT PRIMARY KEY CHECK (id = 1),
    seq  BIGINT   NOT NULL,
    hash VARCHAR(64) NOT NULL
);

-- Genesis: the first entry chains from 64 zeros.
INSERT INTO audit_chain_head (id, seq, hash)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');

-- Points at which the chain head was anchored somewhere else (see AuditAnchor). Verification checks
-- the chain still passes through every one of them, so a tail that was cut off and rebuilt shows.
CREATE TABLE audit_anchors (
    id          BIGSERIAL    PRIMARY KEY,
    seq         BIGINT       NOT NULL,
    hash        VARCHAR(64)  NOT NULL,
    target      VARCHAR(100) NOT NULL,
    anchored_at TIMESTAMPTZ  NOT NULL
);

-- Append-only at the database too: the application never updates or deletes these rows, and nobody
-- else should. (Someone with enough database rights can still disable a trigger - which is exactly
-- what the hash chain then exposes.)
CREATE FUNCTION audit_reject_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit records are append-only (% on %)', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_entries_append_only
    BEFORE UPDATE OR DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION audit_reject_change();

CREATE TRIGGER audit_entries_no_truncate
    BEFORE TRUNCATE ON audit_entries
    FOR EACH STATEMENT EXECUTE FUNCTION audit_reject_change();

CREATE TRIGGER audit_anchors_append_only
    BEFORE UPDATE OR DELETE ON audit_anchors
    FOR EACH ROW EXECUTE FUNCTION audit_reject_change();

CREATE TRIGGER audit_anchors_no_truncate
    BEFORE TRUNCATE ON audit_anchors
    FOR EACH STATEMENT EXECUTE FUNCTION audit_reject_change();
