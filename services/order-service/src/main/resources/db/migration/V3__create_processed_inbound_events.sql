CREATE TABLE processed_inbound_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
