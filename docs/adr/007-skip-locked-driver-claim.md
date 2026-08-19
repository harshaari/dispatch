# ADR-007: Claim drivers with PostgreSQL SKIP LOCKED

## Status

Accepted

## Decision

Dispatch workers claim an available driver inside a short PostgreSQL transaction using `SELECT ... FOR UPDATE SKIP LOCKED`. The selected row is updated to `ASSIGNED` and its assignment is inserted before commit.

## Consequences

This gives local concurrency safety without Redis or a distributed lock service. It favors throughput because blocked workers skip a claimed driver and consider another. A future geographic search can preserve this claim boundary while changing candidate selection.
