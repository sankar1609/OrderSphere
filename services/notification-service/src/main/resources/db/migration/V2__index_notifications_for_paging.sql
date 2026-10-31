-- The inbox is read a page at a time, newest first: one index serves the filter and the order
-- (replacing the recipient-only one).
CREATE INDEX idx_notifications_recipient_username_id ON notifications (recipient_username, id DESC);
DROP INDEX idx_notifications_recipient_username;
