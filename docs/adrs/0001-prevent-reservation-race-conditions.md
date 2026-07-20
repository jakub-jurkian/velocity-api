# [ADR-001] Prevent Ebike Double-Booking Race Conditions

## Status
Accepted

## Context
The Velocity application allows users to reserve e-bikes. Under high traffic volume, a critical database race condition can occur if two users attempt to book the exact same e-bike simultaneously.

If the application relies on a standard read-then-write operation (e.g., checking if the bike's status is `ACTIVE` and then inserting the reservation), both concurrent database transactions may read the `ACTIVE` state at the exact same millisecond before either transaction commits. This results in the database saving two overlapping reservations for a single physical e-bike, leading to hardware unavailability and poor customer experience.

## Decision
We will implement **Optimistic Locking** using JPA's `@Version` annotation on the `BikeInstance` entity.

When a reservation is initiated, the system will read the bike and its current version number. When the reservation is saved and the bike's status is updated to `RENTED`, the transaction will attempt to increment the version. If two transactions attempt this simultaneously, the first will succeed. The second transaction will detect that the database version no longer matches its read version, abort the transaction, and throw an `ObjectOptimisticLockingFailureException`.

This exception will be intercepted by the `GlobalExceptionHandler` and translated into an HTTP 409 Conflict.

## Consequences
*   **Positive Impacts:** Completely eliminates the risk of double-booking physical hardware. It is highly performant because it avoids holding active row-level locks on the database during the transaction.
*   **Negative Impacts:** The user who loses the race condition will experience a booking failure and must manually select a different e-bike. The frontend client must be built to gracefully handle the HTTP 409 response and refresh the available fleet list.

## Alternatives Considered
*   **Pessimistic Locking (`@Lock(LockModeType.PESSIMISTIC_WRITE)`):** Evaluated, but discarded. While it forces the second request to wait in line rather than failing immediately, it holds active database connections open longer. This creates a severe bottleneck during peak hours and introduces the risk of database deadlocks.
*   **PostgreSQL Date-Range Exclusion Constraints (`EXCLUDE USING gist`):** Evaluated, but discarded. While extremely strict and effective for temporal data, it tightly couples our business logic to a specific database vendor (PostgreSQL), breaking framework portability.

## References / Related Links
*   Spring Data JPA Locking Documentation: https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html