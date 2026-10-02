-- Who created the product (the vendor or admin to alert when it runs low). NULL for products that
-- predate this column - their low-stock alerts go to notification-service's fallback recipient.
ALTER TABLE products ADD COLUMN created_by VARCHAR(255);
