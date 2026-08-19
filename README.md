# Dispatch

Dispatch is a portfolio backend for practicing and demonstrating reliable order processing. M1 intentionally begins as one Kotlin/Spring MVC service backed by PostgreSQL; Kafka, Redis, Kubernetes, payment, and driver dispatch come later only when they solve a concrete problem.

## Repository layout

```text
services/order-service/     Kotlin/Spring Boot order service
services/payment-service/   Kotlin/Spring Boot payment service (M3 in progress)
services/dispatch-service/  Kotlin/Spring Boot driver assignment service (M6 in progress)
docs/architecture/          implementation contracts and diagrams
docs/adr/                   architecture decision records
infrastructure/docker/      container build files
.github/workflows/          continuous integration
```

The interview-oriented M1 design is in [the implementation contract](docs/architecture/dispatch-m1-implementation-contract.md). M2's event delivery boundary is specified in [the outbox and Kafka contract](docs/architecture/dispatch-m2-outbox-kafka-contract.md), and M3's asynchronous payment workflow is described in [its implementation contract](docs/architecture/dispatch-m3-payment-workflow-contract.md).
The rationale for the core tradeoffs is captured in [architecture decision records](docs/adr/README.md).

## Prerequisites

- Java 21
- Docker with Docker Compose

The Gradle wrapper is included; a local Gradle installation is not required.

## Build and test

```bash
./gradlew build
```

The integration test starts a real PostgreSQL container, runs Flyway, starts the Spring context, and verifies the expected M1 tables.

## Run locally

Start PostgreSQL and Kafka:

```bash
docker compose up -d postgres broker
./gradlew :services:order-service:bootRun
```

Then inspect:

- readiness: `http://localhost:8080/actuator/health/readiness`
- liveness: `http://localhost:8080/actuator/health/liveness`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- order-service Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- payment-service readiness: `http://localhost:8081/actuator/health/readiness`
- payment-service Prometheus metrics: `http://localhost:8081/actuator/prometheus`
- dispatch-service readiness: `http://localhost:8082/actuator/health/readiness`

The service stores an `OrderCreated` event with each newly created order and relays it to Kafka topic `dispatch.order.events.v1`. Replaying the same idempotency key does not create a second event.

To build the service image first and run the entire local stack:

```bash
./gradlew :services:order-service:bootJar :services:payment-service:bootJar
docker compose --profile app up --build
```

## M1 API

- `POST /api/v1/orders` creates an order with an `Idempotency-Key`.
- `GET /api/v1/orders/{orderId}` returns the stored order and items.
- `POST /api/v1/orders/{orderId}/cancel` cancels an eligible order.

The create endpoint returns `201 Created` and stores its response for same-key/same-request retries. Reusing a key with a different request returns `409 Conflict`. Client-provided `unitPriceMinor` is an intentional M1 simulation boundary; a real service would obtain authoritative prices from a catalog/pricing domain.
