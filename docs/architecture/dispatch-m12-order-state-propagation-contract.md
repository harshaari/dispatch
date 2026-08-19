# Dispatch — M12 Order State Propagation Contract

Dispatch publishes `DriverAssigned`, `OrderPickedUp`, and `OrderDelivered` events. Order service consumes them idempotently and advances only valid order states. Dispatch remains the source of truth for assignments; order service remains the source of truth for customer-visible order state.
