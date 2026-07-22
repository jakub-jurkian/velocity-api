# [ADR-001] Prevent E-Bike Double-Booking Under Concurrency

## Status
Accepted, revised 2026-07-22. Replaces the initial optimistic-locking draft, which was never implemented.

## Context
Clients reserve a specific `BikeInstance` for a date range (`start_date`, `end_date`). Two problems must be solved, and they are not the same problem:

1. **The overlap invariant.** No two *active* reservations (`PENDING` or `CONFIRMED`) for the same bike may cover intersecting dates. This must hold at all times — it is a data-integrity guarantee, not merely a concurrency concern.
2. **The concurrent-insert race.** Two requests can `POST /reservations` for the same bike and overlapping dates simultaneously. Under the default `READ_COMMITTED` isolation, a service-side "is it free?" check does not close the race: both transactions read "free" before either commits (check-then-act), and both `INSERT`.

Two tempting solutions do **not** work here:
- **`@Version` (optimistic locking) on the reservation** cannot stop the insert race: two new rows never share a version counter, so no version check ever fires.
- **A plain `UNIQUE` constraint** cannot express "no overlapping ranges": `[Jul 10, Jul 15)` and `[Jul 12, Jul 14)` are different tuples and both insert cleanly.

## Decision
Enforce the overlap invariant with a **PostgreSQL exclusion constraint**, backed by a friendly service-layer pre-check for UX.

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservations
  ADD CONSTRAINT no_overlapping_active_reservations
  EXCLUDE USING gist (
    bike_instance_id WITH =,
    daterange(start_date, end_date, '[)') WITH &&
  )
  WHERE (status IN ('PENDING', 'CONFIRMED'));
```

Delivered as a raw `<sql>` Liquibase changeset (the constraint is beyond `<addUniqueConstraint>`).

- The **exclusion constraint is the hard guarantee.** It holds regardless of how many application instances run, with no deadlock surface, and closes the concurrent-insert race as a side effect: the second writer blocks on the first, then fails once the first commits.
- A **service-layer availability check** runs first so the common case returns a clean, helpful `409` before ever touching the constraint. This is UX, not the guarantee — the constraint is the guarantee.
- **`@Version` stays on `Reservation`** for its actual job: lost-update races on *state transitions* of an existing row (e.g. a client's cancel racing the `@Scheduled` auto-complete job). That is a different problem from double-booking and is handled in #17.

Range bounds are `[)` (inclusive start, exclusive end) to match the pricing rule `days = ChronoUnit.DAYS.between(start, end)`, which treats `end_date` as exclusive. This makes back-to-back bookings (`10→11` then `11→12`) valid rather than phantom conflicts. The constraint bounds and the calculator's day semantics MUST agree; both are pinned by shared test data (#10, #14). Note: use `[)`, not `[]` — an inclusive-end range would block legal back-to-back rentals.

## Consequences

### Positive
- Double-booking is structurally impossible for active reservations, enforced by the database on every write path — present and future — not by discipline in each service method.
- No row locks held for the transaction's duration; no deadlock surface.
- The guarantee survives horizontal scaling (multiple app instances) with zero extra work.

### Negative
- **Postgres-specific.** `EXCLUDE USING gist` + `btree_gist` do not port to other databases. Accepted: the project is committed to Postgres across dev, CI, and deploy, and a DB-enforced guarantee is worth more than portability we will never exercise.
- The losing request must handle a `409` and refresh availability. `DataIntegrityViolationException` (constraint) and `ObjectOptimisticLockingFailureException` (update races, #17) both surface as `409` in `@RestControllerAdvice`. The latter is thrown at flush/commit, so it must be caught in the advice — not in a `try/catch` around `save()`.
- Correctness depends on the concurrent-request integration test (#15) actually spawning two threads and proving the second booking is rejected.

## Alternatives Considered
- **Optimistic lock (`@Version`) on `BikeInstance` + a `RENTED` status** — rejected. A bike in a date-range system is never globally "rented"; it is booked per interval, so a single flag cannot represent its availability. And even for simultaneous writes it only catches literal same-instant collisions on the shared row — two overlapping bookings made seconds apart both read a fresh version, both commit, and double-book. Wrong tool for an interval invariant. (`@Version` on the bike remains legitimate for a *different* feature: concurrent admin edits to a bike's own status, #20.)
- **Pessimistic lock (`SELECT ... FOR UPDATE`) on the bike row** — rejected. Serializes all bookings per bike (throughput cost at peak) and relies on every write path remembering to take the lock — a new endpoint that forgets silently reintroduces the bug. Adds no safety the constraint doesn't already provide.
- **`SERIALIZABLE` isolation + retry** — rejected. Correct, but spreads serialization-failure handling and retry logic across the whole app for a guarantee the constraint provides locally and permanently.

## References
- PostgreSQL — Exclusion Constraints and the `btree_gist` extension
- PostgreSQL — Range Types (`daterange`, `&&`)
- Spring Data JPA — Locking (`@Version`, `ObjectOptimisticLockingFailureException`)