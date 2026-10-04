-- The provider's own records of every checkout: what was asked for, and whether (and when) it was
-- charged and refunded. charged_at / refunded_at drive the settlement report merchants reconcile
-- against.
CREATE TABLE checkout_sessions (
    id                  VARCHAR(64)    PRIMARY KEY,
    merchant_reference  VARCHAR(255)   NOT NULL,
    amount              NUMERIC(19, 2) NOT NULL,
    currency            VARCHAR(8)     NOT NULL,
    description         VARCHAR(512),
    success_url         VARCHAR(1024),
    cancel_url          VARCHAR(1024),
    webhook_url         VARCHAR(1024),
    status              VARCHAR(16)    NOT NULL,
    charge_reference    VARCHAR(64)    UNIQUE,
    refund_reference    VARCHAR(64),
    created_at          TIMESTAMP      NOT NULL,
    expires_at          TIMESTAMP      NOT NULL,
    charged_at          TIMESTAMP,
    refunded_at         TIMESTAMP
);

CREATE INDEX idx_checkout_sessions_charged_at ON checkout_sessions (charged_at);
CREATE INDEX idx_checkout_sessions_refunded_at ON checkout_sessions (refunded_at);
