CREATE TABLE payment_methods (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_username VARCHAR(255) NOT NULL,
    type              VARCHAR(16)  NOT NULL,
    token             VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP    NOT NULL
);

CREATE TABLE payments (
    id                 BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id           BIGINT        NOT NULL UNIQUE,
    customer_username  VARCHAR(255)  NOT NULL,
    payment_method_id  BIGINT        NOT NULL REFERENCES payment_methods (id),
    amount             NUMERIC(19,2) NOT NULL,
    currency           VARCHAR(8)    NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    gateway_reference  VARCHAR(255),
    retry_count        INTEGER       NOT NULL DEFAULT 0,
    created_at         TIMESTAMP     NOT NULL,
    updated_at         TIMESTAMP     NOT NULL
);

CREATE TABLE refunds (
    id                 BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id         BIGINT        NOT NULL REFERENCES payments (id),
    amount             NUMERIC(19,2) NOT NULL,
    reason             VARCHAR(255)  NOT NULL,
    status             VARCHAR(16)   NOT NULL,
    gateway_reference  VARCHAR(255),
    created_at         TIMESTAMP     NOT NULL
);

CREATE INDEX idx_payment_methods_customer_username ON payment_methods (customer_username);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_refunds_status ON refunds (status);
