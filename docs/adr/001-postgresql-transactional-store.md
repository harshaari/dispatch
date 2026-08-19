# ADR-001: Use PostgreSQL as the M1 transactional store

## Status

Accepted

## Context

M1 needs durable orders, items, idempotency records, database constraints, and transactions. Its target is 500 orders per second at normal peak and 1,000 per second in bursts.

## Decision

Use PostgreSQL as the authoritative transactional store. Flyway owns schema changes. PostgreSQL constraints enforce data invariants alongside application validation.

## Consequences

The initial design is simple to run locally, works with Spring Data JPA, and gives us unique constraints and transactions for correctness. We will measure and evolve only if observed workload requires partitioning, replicas, or another store.
