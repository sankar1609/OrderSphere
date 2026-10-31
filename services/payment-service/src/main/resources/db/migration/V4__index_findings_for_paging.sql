-- Findings are read a page at a time, last seen first (id breaks ties: a run stamps every finding it
-- sees with the same time). This index serves the filter and the order, replacing the status-only one.
CREATE INDEX idx_reconciliation_findings_status_seen
    ON reconciliation_findings (status, last_seen_at DESC, id DESC);
DROP INDEX idx_reconciliation_findings_status;
