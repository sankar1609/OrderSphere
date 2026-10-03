-- Each microservice gets its own database. Add one CREATE DATABASE per
-- service as it's scaffolded (orders_db, payment_db, ...).
CREATE DATABASE auth_db;
CREATE DATABASE inventory_db;
CREATE DATABASE orders_db;
CREATE DATABASE payment_db;
CREATE DATABASE shipping_db;
CREATE DATABASE notification_db;
-- The dummy payment provider (stands in for an external service).
CREATE DATABASE gateway_db;
