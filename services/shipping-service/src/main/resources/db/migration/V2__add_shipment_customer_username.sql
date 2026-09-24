ALTER TABLE shipments ADD COLUMN customer_username VARCHAR(255) NOT NULL DEFAULT 'unknown';
ALTER TABLE shipments ALTER COLUMN customer_username DROP DEFAULT;
