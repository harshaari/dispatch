# Dispatch — M1 Implementation Contract v0.1

## Purpose and interview story

M1 proves a narrow but production-minded claim: an order can be created, retrieved, and cancelled reliably under retries and concurrency. The design favors correctness mechanisms that can be explained and tested—database constraints, short transactions, idempotency, and optimistic locking—over an impressive-looking collection of infrastructure.

```text
Client -> Order Service -> PostgreSQL
```

Target capacity is 500 orders/second at normal peak and 1,000 orders/second in a burst. These are design targets, not benchmark claims. PostgreSQL is the source of truth, Spring MVC is the synchronous request model, and the service runs on Java 21 with Kotlin.

## Scope

M1 will provide:

- `POST /api/v1/orders`
- `GET /api/v1/orders/{orderId}`
- `POST /api/v1/orders/{orderId}/cancel`
- validation and state-machine enforcement
- race-safe idempotency
- PostgreSQL persistence and Flyway migrations
- structured errors and request-correlated logging
- OpenAPI, unit tests, PostgreSQL integration tests, Docker Compose, and CI

M1 explicitly excludes Kafka, Redis, Payment Service, Dispatch Service, Kubernetes, Prometheus, and OpenTelemetry. No external network call belongs inside an M1 database transaction.

## Technology choices

- Kotlin on the Java 21 JVM
- Spring Boot with Spring MVC
- Spring Data JPA/Hibernate and HikariCP
- PostgreSQL and Flyway
- Gradle Kotlin DSL
- JUnit 5, MockK, and Testcontainers
- SpringDoc OpenAPI
- Docker Compose and GitHub Actions

JPA supplies CRUD mapping, transaction management, and optimistic locking. It does not replace SQL knowledge: later locking or outbox polling queries such as `FOR UPDATE SKIP LOCKED` should use explicit SQL when behavior matters.

## Package direction

The service uses feature/domain-oriented boundaries beneath `dev.dispatch.order`:

```text
api/             controllers, request/response DTOs, exception handling
domain/          Order, OrderItem, Money, status and state machine
application/     create, get and cancel use cases
persistence/     JPA entities and repositories
idempotency/     request hashing and conflict handling
error/           domain exceptions and stable error codes
```

Avoid giant project-wide `controller`, `service`, `repository`, `model`, and `util` buckets. Concrete packages and classes are added with the first vertical slice, not as empty placeholders.

## Identity and API contracts

Order IDs are application-generated UUID v4 values. UUID v7 may improve index locality, but M1 does not add a dependency or custom implementation without evidence that it matters.

### Create order

```http
POST /api/v1/orders
Idempotency-Key: 4b869be8-...
Content-Type: application/json
```

```json
{
  "customerId": "cust_123",
  "merchantId": "merchant_456",
  "items": [{ "sku": "burger_001", "quantity": 2, "unitPriceMinor": 1299 }],
  "currency": "USD",
  "deliveryAddress": { "latitude": 35.61, "longitude": -78.74 },
  "paymentMethodId": "pm_123"
}
```

Successful creation returns `201 Created`, a `Location` header, and a persisted resource whose initial status is `PAYMENT_PENDING`:

```json
{
  "orderId": "9d595868-b477-4bca-9d6f-a7089027de5d",
  "status": "PAYMENT_PENDING",
  "totalAmountMinor": 2598,
  "currency": "USD",
  "createdAt": "2026-08-19T19:20:00Z"
}
```

`201` describes successful resource creation even though the wider business workflow is pending. In M1, `unitPriceMinor` is a simulation boundary: the server computes `sum(unitPriceMinor * quantity)`, but a real catalog/pricing domain must authenticate prices.

### Retrieve and cancel

`GET /api/v1/orders/{orderId}` returns the complete order and items; a missing ID returns `404`. `POST /api/v1/orders/{orderId}/cancel` returns `200` with the new `CANCELLED` state. Returning the state is more useful than `204`. An illegal transition returns `409`.

## Validation

Creation rejects a missing `Idempotency-Key`, blank customer or merchant IDs, no items, quantity at or below zero, negative unit price, unsupported currency, latitude outside `-90..90`, longitude outside `-180..180`, blank payment method ID, and duplicate SKUs. M1 supports only `USD`.

## State machine

```text
PAYMENT_PENDING   -> PAYMENT_CONFIRMED | PAYMENT_FAILED | CANCELLED
PAYMENT_CONFIRMED -> DISPATCH_PENDING | CANCELLED
DISPATCH_PENDING  -> DRIVER_ASSIGNED | CANCELLED
DRIVER_ASSIGNED   -> PICKED_UP | CANCELLED
PICKED_UP         -> DELIVERED
```

`PAYMENT_FAILED`, `DELIVERED`, and `CANCELLED` are terminal. M1 publicly exposes only cancellation; the other states establish a stable domain vocabulary for later milestones.

## Persistence contract

The authoritative SQL is `V1__create_order_schema.sql`. Its essential invariants are:

- `orders.id` and `order_items.id` are UUID primary keys.
- status has both an application enum and a database `CHECK` constraint.
- money is stored in integer minor units and cannot be negative.
- coordinates are constrained by PostgreSQL.
- `(order_id, sku)` is unique, preventing duplicate SKUs structurally.
- `orders.version` supports JPA optimistic locking.
- `(operation, idempotency_key)` is unique.
- the initial business index is `(customer_id, created_at DESC)`.

No merchant history index is added until a merchant query exists: indexes follow access patterns rather than decoration.

## Idempotency contract

The key scope is `(operation, idempotency_key)`, with `CREATE_ORDER` as the initial operation. It is not scoped by customer because M1 has no trustworthy authenticated customer identity. Records retain a request hash, resulting resource ID, HTTP status, response JSON, creation time, and an expiry 48 hours later. Cleanup is documented but not implemented in M1; a production job should delete or archive expired rows in bounded batches.

### Canonical request hashing

Semantics are:

```text
same key + same logical request -> replay original success
same key + different request    -> 409 IDEMPOTENCY_CONFLICT
```

Deserialize and validate first, serialize a deterministic canonical representation, then compute SHA-256. Do not hash raw JSON bytes because property order and insignificant formatting do not change the logical request.

### Single-transaction algorithm

`CreateOrderService.createOrder()` is one short database transaction at PostgreSQL's default `READ COMMITTED` isolation:

1. Attempt an idempotency-record insert using `ON CONFLICT (operation, idempotency_key) DO NOTHING`.
2. If inserted, create the order and items, compute the total, store the `201` response body/status on the idempotency record, and commit.
3. If the insert conflicts, read the existing record.
4. A different hash returns `409`; a matching hash replays the stored response without creating an order.

PostgreSQL's unique index serializes concurrent requests with the same key. If the owner commits, waiters observe and replay its result; if it rolls back, its reservation disappears. Because M1 performs no remote call, it needs no `STARTED` status, leases, lock stealing, or zombie-record recovery.

Storing the response body preserves the result when creation commits but the client loses the HTTP response. Only the payload and necessary status are stored, not arbitrary headers.

## Cancellation concurrency

`orders.version` is mapped with JPA `@Version`. Updates include the version in the predicate and increment it. If a concurrent transition wins, a zero-row update becomes an optimistic-lock conflict; reload the order and decide whether the requested transition remains legal. Concurrency correctness relies on constraints, transaction boundaries, and optimistic locking—not globally raising isolation to `SERIALIZABLE`.

## Error model

Every error has a stable code, safe message, request ID, and UTC timestamp:

```json
{
  "code": "INVALID_STATE_TRANSITION",
  "message": "Order cannot transition from DELIVERED to CANCELLED.",
  "requestId": "req_...",
  "timestamp": "2026-08-19T19:20:00Z"
}
```

| Situation | HTTP status |
|---|---:|
| Validation failure or missing idempotency key | 400 |
| Order not found | 404 |
| Same key with different request | 409 |
| Invalid transition or optimistic-lock conflict | 409 |
| Unexpected server failure | 500 |

## Logging and health

Accept a valid inbound `X-Request-Id` or generate one, place it in structured logs, and include it in errors. Useful fields include service, request ID, order ID, level, timestamp, and event message. Never log authorization tokens, payment credentials, or whole request bodies.

Expose `/actuator/health/liveness` and `/actuator/health/readiness`. PostgreSQL participates in readiness because the service cannot fulfill its core responsibility without its source of truth.

## Testing contract

Prefer meaningful integration tests over mock-heavy coverage.

Unit tests cover state transitions, money arithmetic, validation, canonicalization, and hashing. PostgreSQL Testcontainers tests cover repository behavior, migrations and constraints, rollback, optimistic locking, replay, same-key/different-payload conflict, and many concurrent requests producing exactly one order. HTTP integration tests cover status codes, headers, schemas, and error mapping. CI runs the Gradle build and these container-backed tests.

## Delivery sequence

This repository scaffold is the healthy baseline. The next change should implement one vertical create-order slice with its tests, followed by retrieve and cancel behavior. Later milestones introduce the transactional outbox and Kafka, a payment service, concurrency-safe driver dispatch, resilience, Redis only for a demonstrated access pattern, observability, load testing, and deployment packaging.

The central interview takeaway is deliberate restraint: M1 solves reliable local transactions completely before distributed components create new failure modes.
