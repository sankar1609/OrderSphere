CREATE TABLE products (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku               VARCHAR(64)  NOT NULL UNIQUE,
    name              VARCHAR(255) NOT NULL,
    quantity_on_hand  INTEGER      NOT NULL DEFAULT 0,
    quantity_reserved INTEGER      NOT NULL DEFAULT 0,
    reorder_threshold INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL
);

CREATE TABLE reservations (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT      NOT NULL UNIQUE,
    status     VARCHAR(16) NOT NULL,
    expires_at TIMESTAMP,
    created_at TIMESTAMP   NOT NULL
);

CREATE TABLE reservation_items (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id    BIGINT  NOT NULL REFERENCES reservations (id),
    product_id        BIGINT  NOT NULL REFERENCES products (id),
    quantity_reserved INTEGER NOT NULL
);

CREATE TABLE backorders (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id   BIGINT      NOT NULL REFERENCES products (id),
    order_id     BIGINT      NOT NULL,
    quantity     INTEGER     NOT NULL,
    status       VARCHAR(16) NOT NULL,
    created_at   TIMESTAMP   NOT NULL,
    fulfilled_at TIMESTAMP
);

CREATE INDEX idx_backorders_product_status ON backorders (product_id, status);
CREATE INDEX idx_reservations_status_expires_at ON reservations (status, expires_at);
