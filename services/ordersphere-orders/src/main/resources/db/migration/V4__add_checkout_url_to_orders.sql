-- Where the customer pays for the order on the payment provider's hosted checkout page.
ALTER TABLE orders ADD COLUMN checkout_url VARCHAR(1024);
