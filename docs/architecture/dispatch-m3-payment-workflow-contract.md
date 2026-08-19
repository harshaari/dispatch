# Dispatch — M3 Payment Workflow Contract

## Purpose and scope

M3 introduces a deliberately small payment-service boundary. It turns the `PAYMENT_PENDING` order state into a real asynchronous workflow without placing a remote payment call inside order creation.

```text
Order Service                      Payment Service
-------------                      ---------------
orders + OrderCreated ──Kafka──►   idempotent payment decision
                                      + PaymentAuthorized or PaymentDeclined
orders status ◄────────────Kafka──  transactional outbox
```

M3 includes a deterministic local payment gateway simulator only. It is an adapter seam, not a claim that the project processes card data or integrates with a live provider.

## Ownership and transport

`payment-service` owns its payment records and its PostgreSQL database. It consumes `OrderCreated` from `dispatch.order.events.v1` and publishes payment results to `dispatch.payment.events.v1`. Its consumer records processed event IDs before completing work, so Kafka redelivery cannot create a second payment decision.

The order service consumes payment results and changes only an eligible `PAYMENT_PENDING` order. It writes the resulting order-state event to its existing outbox in the same database transaction. Repeated or stale payment results are harmless.

## Events

Payment events use the established M2 envelope convention and use the order UUID as their Kafka key.

`PaymentAuthorized` data:

```json
{
  "paymentId": "uuid",
  "orderId": "uuid",
  "status": "AUTHORIZED",
  "amountMinor": 2598,
  "currency": "USD"
}
```

`PaymentDeclined` has the same identifiers and amount fields plus a non-sensitive machine-readable `reason` such as `PAYMENT_METHOD_DECLINED`. Payment method IDs, delivery coordinates, and gateway request/response bodies are never published or logged.

## Payment decision and state rules

The simulator authorizes normal test method IDs and declines IDs with the explicit `pm_decline_` prefix. The rule exists only to make happy and failure paths executable locally; changing to a real provider later happens behind the gateway interface.

| Current order status | Result | New order status |
| --- | --- | --- |
| `PAYMENT_PENDING` | `PaymentAuthorized` | `PAYMENT_CONFIRMED` |
| `PAYMENT_PENDING` | `PaymentDeclined` | `PAYMENT_FAILED` |
| Any other state | Either result | no change |

Cancellation remains authoritative: a late payment result never resurrects a `CANCELLED` order. This is a business choice for the portfolio workflow; a production system would separately define void/refund compensation.

## Delivery and verification

Both services use the M2 transactional-outbox relay. Delivery remains at least once, so each consumer has an idempotency boundary and every event includes an ID. M3 verifies: authorization, decline, duplicate `OrderCreated`, a late result after cancellation, and a full Docker Compose workflow with two PostgreSQL databases and Kafka.
