ALTER TABLE orders ADD COLUMN payment_id BIGINT;
ALTER TABLE orders ADD COLUMN shipment_id BIGINT;
ALTER TABLE orders ADD COLUMN shipping_destination VARCHAR(255);
