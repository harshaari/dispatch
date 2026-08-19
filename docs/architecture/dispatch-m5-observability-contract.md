# Dispatch — M5 Observability Contract

M5 exposes a small operational baseline for each service. Spring Boot Actuator provides liveness and readiness probes; Micrometer’s Prometheus registry exposes JVM, HTTP server, Hikari, and Kafka client metrics at `/actuator/prometheus`.

These endpoints are suitable for local inspection and a later Prometheus scrape configuration. They do not expose request bodies, payment references, or Kafka payloads. Structured logs continue to carry correlation fields such as request ID, order ID, payment ID, and event ID.

M5 intentionally stops short of distributed tracing, dashboards, alerts, or a production monitoring deployment. Those need service-level objectives and an actual deployment environment first.
