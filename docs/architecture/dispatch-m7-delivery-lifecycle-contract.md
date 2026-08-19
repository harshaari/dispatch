# Dispatch — M7 Delivery Lifecycle Contract

M7 makes dispatch assignments explicit resources. Dispatch owns the transitions `ASSIGNED → PICKED_UP → DELIVERED`; invalid or repeated transitions are rejected. Completing an assignment releases the driver back to `AVAILABLE` in the same transaction.

The initial endpoints are intentionally internal development controls, not a driver-facing mobile API. Authentication, location evidence, cancellation compensation, and delivery notifications remain later milestones.
