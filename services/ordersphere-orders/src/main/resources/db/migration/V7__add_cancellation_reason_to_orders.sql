-- Why an order was cancelled, in words the customer can read (e.g. "Not enough stock: ...").
ALTER TABLE orders ADD COLUMN cancellation_reason VARCHAR(255);
