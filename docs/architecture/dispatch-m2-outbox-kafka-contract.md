# Dispatch — M2 Outbox and Kafka Contract

## Purpose and scope

M2 adds one asynchronous integration boundary to the implemented M1 order service:

```text
create order transaction
    ├── orders / order_items / idempotency_records
    └── outbox_events
                 │
                 ▼
        scheduled outbox relay ──► Kafka: dispatch.order.events.v1
```

The only M2 event is `OrderCreated`. Kafka is not part of the client request path: `POST /api/v1/orders` continues to return `201 Created` after the PostgreSQL transaction commits. No payment, driver, notification, or consumer service is introduced in this milestone.

## Event contract

Topic: `dispatch.order.events.v1`  
Kafka key: the order UUID, preserving ordering per order.

Each record is JSON with this stable envelope:

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "schemaVersion": 1,
  "occurredAt": "2026-08-19T16:00:00Z",
  "data": {
    "orderId": "uuid",
    "customerId": "cust_123",
    "merchantId": "merchant_456",
    "status": "PAYMENT_PENDING",
    "totalAmountMinor": 2598,
    "currency": "USD"
  }
}
```

The event deliberately excludes the payment-method identifier and delivery coordinates. Consumers receive only the information needed to begin their own workflow. `schemaVersion` allows compatible evolution without silently redefining an existing event.

## Atomic write rule

For a first successful create request, the order, items, idempotency response, and one `outbox_events` row are written in the same PostgreSQL transaction. If that transaction rolls back, none of them exist. A same-key/same-request idempotency replay returns the stored response and creates no second outbox event.

## Relay algorithm and delivery semantics

The relay runs on a fixed delay and claims a small batch of unpublished rows with PostgreSQL `FOR UPDATE SKIP LOCKED`. Claiming stores a relay identity and a short `locked_until` lease, then commits before contacting Kafka. This prevents one relay instance from holding a database transaction open during a network call, while allowing another instance to recover work after a crash.

For every claimed event, the relay waits for Kafka's broker acknowledgement. It marks the row published only after that acknowledgement. A failure records the error, increments `attempts`, clears the lease, and leaves the row eligible for a later retry. A process failure after Kafka accepts a record but before PostgreSQL records `published_at` can cause a duplicate publication; therefore the guarantee is **at least once** and consumers must deduplicate by `eventId`.

This is intentionally not a distributed transaction or a claim of end-to-end exactly-once processing. Kafka's normal delivery model permits retries and redelivery; the outbox removes the more dangerous failure where a committed order has no durable event to retry.

## Storage and operation

`outbox_events` contains an immutable payload plus publishing state:

- `id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload`, `occurred_at`
- `published_at` once broker acknowledgement is known
- `locked_by`, `locked_until` for relay coordination
- `attempts`, `last_error` for operator inspection

An index on pending events supports relay scans. The default relay configuration is intentionally modest: a 250 ms delay, a batch size of 50, and a 30-second lease. These values are configuration, not API promises, and should be tuned from production evidence.

## Verification

M2 has an end-to-end integration test using PostgreSQL and Kafka Testcontainers. It creates an order, asserts exactly one durable outbox record, invokes the relay, consumes the Kafka record, and verifies the event envelope and key. Existing M1 integration tests keep the background relay disabled so they remain focused on the synchronous API.
