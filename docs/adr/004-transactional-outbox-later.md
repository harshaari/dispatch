# ADR-004: Introduce the transactional outbox with Kafka in M2

## Status

Accepted

## Context

Publishing an event after committing an order creates a dual-write failure mode. M1 intentionally has no Kafka or downstream services.

## Decision

M1 did not create an outbox table or publisher. M2 introduces the transactional outbox with Kafka, where the order update and outbox event are committed atomically before a publisher delivers events at least once.

## Consequences

M1 avoided unused infrastructure while preserving the correct future boundary. M2 consumers must be idempotent because delivery is at least once. The M2 event and relay details live in the [M2 outbox and Kafka contract](../architecture/dispatch-m2-outbox-kafka-contract.md).
