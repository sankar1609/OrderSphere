-- The admin lists are read a page at a time, in a stable order (id breaks ties between rows updated
-- at the same instant): one index per list serves the filter and the order without sorting.
CREATE INDEX idx_order_compensations_status_updated
    ON order_compensations (status, updated_at DESC, id DESC);

CREATE INDEX idx_orders_unshipped_updated ON orders (updated_at, id)
    WHERE status = 'CONFIRMED' AND shipment_id IS NULL;
