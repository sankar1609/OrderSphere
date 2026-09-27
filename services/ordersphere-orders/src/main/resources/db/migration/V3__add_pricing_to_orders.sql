-- Nullable: orders placed before pricing existed have no stored price or total.
ALTER TABLE orders ADD COLUMN total_amount NUMERIC(12, 2);
ALTER TABLE orders ADD COLUMN currency VARCHAR(3);
ALTER TABLE order_items ADD COLUMN unit_price NUMERIC(12, 2);
