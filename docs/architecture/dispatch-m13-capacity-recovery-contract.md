# Dispatch — M13 Capacity Recovery Contract

When no driver is available, dispatch stores a pending assignment instead of failing the payment event. A scheduled worker retries pending orders using the same `SKIP LOCKED` claim path. This keeps the payment event durable and makes capacity wait visible in the dispatch database.
