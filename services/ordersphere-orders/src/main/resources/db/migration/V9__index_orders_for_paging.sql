-- A customer's order list is read a page at a time, newest first: one index serves the filter and
-- the order (replacing the customer-only one), and order_items is found by order id - the items
-- of a whole page in one query (hibernate.default_batch_fetch_size) instead of a scan per order.
CREATE INDEX idx_orders_customer_username_id ON orders (customer_username, id DESC);
DROP INDEX idx_orders_customer_username;

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
