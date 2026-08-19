# Dispatch — M6 Driver Assignment Contract

M6 adds `dispatch-service`, which consumes `PaymentAuthorized` and creates one driver assignment for the corresponding order.

```text
PaymentAuthorized ──► dispatch-service ──► driver claim + assignment
```

The service owns drivers and assignments in its own PostgreSQL database. A seeded local driver pool makes the workflow executable without a location provider. An assignment transaction selects an available driver using `FOR UPDATE SKIP LOCKED`, marks that driver assigned, and inserts the assignment. Competing workers skip locked candidates, so one driver cannot be assigned twice.

The inbound event ID is stored before completion of the transaction. Redelivered events are no-ops. If no driver is available, the transaction fails and Kafka retry/DLT handling from M4 preserves the event for recovery; it is not silently dropped. M6 does not yet model driver location, reassignment, delivery completion, or route optimization.
