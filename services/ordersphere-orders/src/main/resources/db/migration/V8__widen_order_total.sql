-- A single product may cost up to 9,999,999,999.99 (inventory's NUMERIC(12, 2)), so an order of
-- two of them overflowed NUMERIC(12, 2) here. Match payment-service's NUMERIC(19, 2): even the
-- largest allowed order (50 lines x 10,000 units at the top price) fits.
ALTER TABLE orders ALTER COLUMN total_amount TYPE NUMERIC(19, 2);
