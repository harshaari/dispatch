# ADR-005: Use a monorepo with service-owned data

## Status

Accepted

## Context

Dispatch will grow from one service into order, payment, and dispatch services. The portfolio should be easy to clone, run, and inspect as one cohesive system.

## Decision

Use a Gradle monorepo. Each future service owns its data and migrations; cross-service workflow uses events rather than direct database access.

## Consequences

The repository remains approachable during early development while preserving the ownership boundaries needed when services are added. Shared code remains deliberately small to avoid a distributed monolith.
