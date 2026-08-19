# ADR-004: Defer the transactional outbox until Kafka exists

## Status

Accepted

## Context

Publishing an event after committing an order creates a dual-write failure mode. M1 intentionally has no Kafka or downstream services.

## Decision

Do not create an outbox table or publisher in M1. Introduce the transactional outbox in the Kafka milestone, where the order update and outbox event can be committed atomically before a publisher delivers events at least once.

## Consequences

M1 avoids unused infrastructure while preserving the correct future boundary. When introduced, consumers must still be idempotent because delivery is at least once.
