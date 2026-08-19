CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_amount_minor BIGINT NOT NULL CHECK (total_amount_minor >= 0),
    currency CHAR(3) NOT NULL,
    delivery_latitude DOUBLE PRECISION NOT NULL CHECK (delivery_latitude BETWEEN -90 AND 90),
    delivery_longitude DOUBLE PRECISION NOT NULL CHECK (delivery_longitude BETWEEN -180 AND 180),
    payment_method_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_order_status CHECK (
        status IN (
            'PAYMENT_PENDING', 'PAYMENT_CONFIRMED', 'PAYMENT_FAILED',
            'DISPATCH_PENDING', 'DRIVER_ASSIGNED', 'PICKED_UP',
            'DELIVERED', 'CANCELLED'
        )
    )
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku VARCHAR(128) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price_minor BIGINT NOT NULL CHECK (unit_price_minor >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (order_id, sku)
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_id UUID,
    response_status INTEGER,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE (operation, idempotency_key)
);

CREATE INDEX idx_orders_customer_created
    ON orders (customer_id, created_at DESC);
