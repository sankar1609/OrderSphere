CREATE TABLE shipments (
    id                 BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id           BIGINT       NOT NULL,
    type               VARCHAR(16)  NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    carrier            VARCHAR(64)  NOT NULL,
    tracking_number    VARCHAR(64)  NOT NULL,
    destination        VARCHAR(255) NOT NULL,
    parent_shipment_id BIGINT REFERENCES shipments (id),
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    delivered_at       TIMESTAMP,
    CONSTRAINT uq_shipments_order_id_type UNIQUE (order_id, type)
);

CREATE TABLE shipment_tracking_events (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shipment_id  BIGINT      NOT NULL REFERENCES shipments (id),
    status       VARCHAR(16) NOT NULL,
    location     VARCHAR(255) NOT NULL,
    occurred_at  TIMESTAMP   NOT NULL
);

CREATE INDEX idx_shipments_status ON shipments (status);
CREATE INDEX idx_shipment_tracking_events_shipment_id ON shipment_tracking_events (shipment_id);
