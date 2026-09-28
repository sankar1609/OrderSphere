-- Payments are now collected on the provider's hosted checkout page instead of charging a saved
-- payment method, so a payment no longer references one. The payment_methods table and the
-- payments.payment_method_id / retry_count columns are no longer mapped; they are kept (not
-- dropped) so existing rows aren't lost, and can be removed in a later migration.
ALTER TABLE payments ALTER COLUMN payment_method_id DROP NOT NULL;

ALTER TABLE payments ADD COLUMN checkout_session_id VARCHAR(255);
ALTER TABLE payments ADD COLUMN checkout_url        VARCHAR(1024);
ALTER TABLE payments ADD COLUMN failure_reason      VARCHAR(255);

CREATE UNIQUE INDEX uq_payments_checkout_session_id ON payments (checkout_session_id);

-- Payments still PENDING from the old flow have no checkout session and can never be paid:
-- fail them so the orders saga cancels their orders and releases the stock.
UPDATE payments
SET status = 'FAILED', failure_reason = 'Superseded by hosted checkout', updated_at = NOW()
WHERE status = 'PENDING';
