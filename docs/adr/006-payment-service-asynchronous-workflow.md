# ADR-006: Use an asynchronous payment workflow in M3

## Status

Accepted

## Context

Order creation is a short PostgreSQL transaction. Calling a payment provider from that transaction would hold database resources through an unreliable network boundary and couple client latency to a downstream system.

## Decision

Introduce a payment service that consumes `OrderCreated` asynchronously, makes a payment decision behind a gateway interface, and publishes a durable result through its transactional outbox. The order service consumes that result and changes the order state only when the transition is valid.

## Consequences

The workflow is eventually consistent: newly created orders remain `PAYMENT_PENDING` until the payment event arrives. Consumers must be idempotent and late results must not undo cancellation. The new service owns payment data and does not share the order-service database.
