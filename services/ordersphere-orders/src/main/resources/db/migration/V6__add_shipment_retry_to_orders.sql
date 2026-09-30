-- A paid (CONFIRMED) order whose shipment couldn't be created yet is retried by the saga sweep with
-- back-off. shipment_next_attempt_at is when the next try is due; NULL once it has a shipment or
-- retries have been given up on (see /orders/admin/unshipped).
ALTER TABLE orders
    ADD COLUMN shipment_attempts        INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN shipment_next_attempt_at TIMESTAMP,
    ADD COLUMN shipment_last_error      VARCHAR(1024);

-- Orders confirmed before this existed that never got a shipment: retry them straight away.
UPDATE orders
SET shipment_next_attempt_at = updated_at
WHERE status = 'CONFIRMED' AND shipment_id IS NULL;

CREATE INDEX idx_orders_shipment_due ON orders (shipment_next_attempt_at)
    WHERE status = 'CONFIRMED' AND shipment_id IS NULL;
