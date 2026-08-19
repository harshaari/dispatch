# ADR-002: Use Spring MVC for synchronous order APIs

## Status

Accepted

## Context

M1 is a conventional request/response API backed by PostgreSQL. It does not perform streaming or large numbers of long-lived remote calls.

## Decision

Use Spring MVC rather than WebFlux.

## Consequences

The service uses the familiar servlet request model and JDBC/JPA integration. A reactive stack would add a different programming model without solving a demonstrated M1 problem. Future asynchronous workflow will be introduced through events, not by making the HTTP controller reactive prematurely.
