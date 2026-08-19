# ADR-003: Make order creation idempotent with PostgreSQL

## Status

Accepted

## Context

Clients retry requests when they lose a response. Creating two orders for one client intent is unacceptable. M1 contains no remote side effect inside order creation.

## Decision

Store one record per `(operation, idempotency_key)` with a canonical SHA-256 request hash and the successful response body. Insert the record, order, items, and response in one short PostgreSQL transaction. A unique constraint serializes concurrent same-key requests.

## Consequences

Same key and same logical request replays the original `201` response. Same key and different request returns `409`. Because the whole operation is local and transactional, M1 needs no leases, `STARTED` status, lock stealing, or zombie recovery.
