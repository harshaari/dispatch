# Dispatch Portfolio Demo Runbook

## Story

Create an order with an idempotency key. Order service stores the order and `OrderCreated` outbox event atomically. Payment service authorizes it and publishes a result. Dispatch claims an available driver using `SKIP LOCKED`, then publishes lifecycle events that order service reflects as `DRIVER_ASSIGNED`, `PICKED_UP`, and `DELIVERED`.

## Run

```bash
./gradlew build
./gradlew :services:order-service:bootJar :services:payment-service:bootJar :services:dispatch-service:bootJar
docker compose --profile app up --build
```

Inspect readiness on ports 8080, 8081, and 8082. Dispatch internal controls require `X-Internal-Token: local-dispatch-token` in the local Compose environment.

## Interview points

- PostgreSQL is authoritative for each service; databases are not shared.
- Transactional outboxes remove the order/payment dual-write gap; delivery is at least once.
- Consumers deduplicate events, and `SKIP LOCKED` prevents double driver claims.
- Pending assignments handle temporary capacity exhaustion.
- Kafka retries and DLTs make persistent consumer failures visible and recoverable.
