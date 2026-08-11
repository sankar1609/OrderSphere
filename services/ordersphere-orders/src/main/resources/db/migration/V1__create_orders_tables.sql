CREATE TABLE orders (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_username VARCHAR(255) NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL
);

CREATE TABLE order_items (
    id       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT       NOT NULL REFERENCES orders (id),
    sku      VARCHAR(64)  NOT NULL,
    quantity INTEGER      NOT NULL
);

CREATE INDEX idx_orders_customer_username ON orders (customer_username);
