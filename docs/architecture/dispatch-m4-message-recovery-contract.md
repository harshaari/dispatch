# Dispatch — M4 Message Recovery Contract

M4 makes the Kafka boundaries operationally recoverable before more consumers are introduced. Each service retries a failed record twice with a one-second fixed backoff. If processing still fails, the original record is published to `<source-topic>.DLT` using the same key and partition, then the consumer can continue.

The DLT is an explicit operator signal, not silent data loss. A future replay tool must repair the cause and republish the original record with its event ID; consumer-side idempotency makes that safe. The normal path remains at least once, and transient database failures continue to be retried before dead-lettering.
