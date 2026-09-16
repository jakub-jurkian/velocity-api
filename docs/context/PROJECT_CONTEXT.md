# VeloCity Fleet API — Project Context

Last updated: 2026-09-16

This file is the single source of truth for the project. It reflects the repo state, the architectural decisions captured in ADRs, and the current implementation status for the backend service that powers the VeloCity frontend.

Per ADR-003 this file is living documentation: it is updated at the end of each closed issue, not retroactively. If something here contradicts the code, the code wins and this file is the bug.

## 1. Project goal

VeloCity is an e-bike rental platform. The backend exists to make the booking flow robust, safe, and testable under real-world concurrency and business rules.

The core business risk is preventing double-booking for the same physical bike on overlapping dates, while keeping the domain model clean and the API easy for a React frontend to consume.

The core product workflow is:
- browse available bikes
- register/login as a client
- reserve a bike for a date range
- confirm a reservation (simulated payment)
- auto-complete or auto-cancel lifecycle transitions
- manage users and admin actions

## 2. Current implementation status

The project is in the frontend-integration and hardening stage. The backend is feature-complete for the demo flow; remaining work is production polish and observability.

Implemented:
- JWT authentication with per-request account-status enforcement
- user registration, login, logout, profile updates, admin user lifecycle
- fleet counts and date-range availability with server-side pricing
- reservation creation, ownership checks, lifecycle transitions, scheduled jobs
- admin analytics aggregates
- ProblemDetail-based error handling for domain exceptions
- Postgres-backed reservation integrity via an exclusion constraint
- concurrency, integration, repository and domain tests on Testcontainers

Not yet implemented (see section 13):
- Dockerfile, deployment, Actuator, structured logging
- 401 semantics for unauthenticated requests
- 4xx mapping for several framework-level client errors

## 3. Tech stack

- Java 25
- Spring Boot 4.1.0
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Redis 8 (JWT blacklist)
- Liquibase
- Spring Security + JWT (JJWT 0.12.6)
- Springdoc OpenAPI / Swagger UI
- Maven
- JUnit 5 + Mockito + Spring test support + Testcontainers
- Docker Compose (Postgres + Redis only; the app itself is not containerised yet)
- GitHub Actions

Runtime conventions:
- `spring.profiles.default: dev`, so `./mvnw spring-boot:run` works with no extra flags
- `spring.web.locale: en`, so Bean Validation messages are English regardless of JVM locale
- PostgreSQL on localhost:5432 and Redis on localhost:6379 in local dev
- schema changes are Liquibase-managed; JPA `ddl-auto` is validate-only
- `open-in-view: false`; no lazy loading outside a transaction
- security is stateless with JWT-based filtering
- pricing is BigDecimal-based, configured via the top-level `pricing.daily-rate` key (NOT `spring.pricing.*`)

## 4. Architecture and package structure

```
com.velocity.api
├── analytics    controller, service, dto
├── auth         controller, service, dto
├── bike         entities, controller, service, repository (+projection), dto, exception
├── common       City, dto (PaginatedResponse), exception (GlobalExceptionHandler, ResourceNotFoundException)
├── config       ClockConfig, OpenApiConfig, SecurityConfig
├── pricing      RentalCostCalculator, dto (RentalQuote)
├── reservation  entity, controller, service, repository (+projections), dto, mapper, scheduler, exception
├── security     CustomUserDetails(+Service), JwtService, JwtAuthenticationFilter, repository (TokenBlacklistRepository)
└── user         entity, controller (User + AdminUser), service, repository, dto, exception
```

Architectural rules visible in the codebase:
- controllers are thin HTTP adapters
- services orchestrate transactions and business rules
- repositories handle data access
- entities protect their own invariants
- DTOs are used at the API boundary; entities never leak to the web layer
- global error handling returns RFC 7807 ProblemDetail payloads
- method security enforces owner and admin use cases

## 5. Domain model

### User
Fields: `id` (UUID), `email`, `passwordHash`, `fullName`, `phone`, `status`, `role`, `city`, `createdAt` (Instant, `@CreatedDate`, immutable), `lastModified` (Instant, `@LastModifiedDate`), `version` (`@Version`).

Rules:
- unique email and phone (DB constraints)
- created via `User.registerClient(...)`; constructor is private
- mutation only via `updateProfile(...)`, `updateProfileByAdmin(...)`, `changeRoleByAdmin(...)`, `block()`, `unblock()`
- email is normalised to lowercase and regex-validated inside the entity
- phone must match E.164 (`^\+?[1-9]\d{1,14}$`)
- auditing is enabled by `@EnableJpaAuditing` on the main class

Note: there is no `softDelete()` and no `DELETED` status. `UserStatus` is `ACTIVE, BLOCKED` only. `block()` and `unblock()` are currently silent no-ops when the user is already in the target state, so `InvalidUserStateException` is presently unreachable.

### BikeModel
Fields: `id`, `name` (unique), `description`, `speed`, `range`, `capacity`, `category`.
Created via `BikeModel.create(...)`. Ranges validated in the entity (speed 1-45, range 15-500, capacity 1-100).

### BikeInstance
Fields: `id`, `status`, `city`, `bikeModel` (LAZY `@ManyToOne`).
`BikeStatus`: ACTIVE, MAINTENANCE, LOST, RETIRED. Created via `BikeInstance.initialize(...)`, defaults to ACTIVE. Booking is only allowed when ACTIVE.

### Reservation
Fields: `id`, `startDate`, `endDate`, `totalCost` (BigDecimal), `status`, `createdAt` (Instant, `@CreatedDate`), `user` (LAZY), `bikeInstance` (LAZY), `version` (`@Version`).

`ReservationStatus`: PENDING, CONFIRMED, COMPLETED, CANCELLED.

Rules:
- created via `Reservation.book(...)`; duration must be 3-21 days
- lifecycle enforced by `Reservation.transitionTo(newStatus, currentDate)`
- same-status transition is a deliberate no-op
- cancelling on or after `startDate` throws `LateCancelException`
- invalid transitions throw `InvalidStatusTransitionException`

### Enums shared
`City`: GDANSK, WROCLAW, WARSAW, POZNAN. `UserRole`: CLIENT, ADMIN. `BikeCategory`: AGILITY, HEAVY_DUTY, DUAL_BATTERY.

Important for the frontend: the client role is **CLIENT**, not `USER`.

## 6. Pricing

`RentalCostCalculator.calculateQuote(int rentalDays)` returns a `RentalQuote` record and is the only place money is computed.

```
RentalQuote(int rentalDays, BigDecimal baseDailyRate, BigDecimal effectiveDailyRate,
            int discountPercentage, BigDecimal totalCost)
```

Tiers, applied to `pricing.daily-rate` (25.00 PLN):

| Duration | Multiplier | discountPercentage |
|---|---|---|
| 3-7 days | 1.00 | 0 |
| 8-14 days | 0.80 | 20 |
| 15-21 days | 0.60 | 40 |

Day-count semantics: `days = ChronoUnit.DAYS.between(startDate, endDate)`, so **endDate is exclusive**. Oct 10 to Oct 15 is 5 days. This must stay aligned with the `[)` bounds of the exclusion constraint in ADR-001; changing one without the other creates phantom conflicts or double-bookings.

`totalCost` is computed server-side and persisted as a snapshot. A client-sent price is never trusted, and a later rate change must never alter an existing agreement. The frontend renders the returned quote and does not recompute it.

## 7. Security and auth

Stateless JWT authentication.

- `SecurityConfig`: CSRF disabled, `SessionCreationPolicy.STATELESS`, CORS from the `cors.allowed-origins` property, `@EnableMethodSecurity`
- `JwtAuthenticationFilter`: extracts the bearer token, rejects blacklisted tokens, loads `UserDetails` per request, and refuses blocked accounts via `isAccountNonLocked()`. Failures are handed to the `handlerExceptionResolver`, so they surface as ProblemDetail from `GlobalExceptionHandler` rather than as raw servlet errors.
- `CustomUserDetailsService` resolves authorities as `ROLE_<UserRole>` from persisted data
- token claims: subject is the email; extra claims are `userId` and `role`
- BCrypt password hashing
- Redis blacklists a token on logout for its remaining TTL

Because authorities and account status are re-read from the database on every request, blocking a user takes effect immediately rather than when their token expires.

Public endpoints: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, and the Swagger/OpenAPI paths. Everything else requires authentication.

Known gap: `TokenBlacklistRepository.blacklistUser(...)` is still called by `AdminUserService.blockUser(...)`, but `isUserBlacklisted(...)` is no longer read by the filter. That write is currently dead and the pair should be removed or re-wired.

## 8. Reservation logic and lifecycle

State machine:
- PENDING -> CONFIRMED
- PENDING -> CANCELLED
- CONFIRMED -> COMPLETED
- CONFIRMED -> CANCELLED
- CANCELLED and COMPLETED are terminal

Automation (`ReservationLifecycleScheduler`):
- stale PENDING reservations older than 30 minutes are auto-cancelled
- CONFIRMED reservations whose `endDate` has passed are auto-completed
- each id is processed in its own transaction; `ObjectOptimisticLockingFailureException` is caught per item and logged so one conflict cannot abort the batch

Booking path (`ReservationService.book`): load user and bike, reject non-ACTIVE bikes, run a service-level availability pre-check, price the rental, then save. The pre-check is UX only; the database constraint is the guarantee.

## 9. API surface

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/auth/register` | public |
| POST | `/api/v1/auth/login` | public |
| POST | `/api/v1/auth/logout` | authenticated |
| GET | `/api/v1/auth/me` | authenticated |
| PATCH | `/api/v1/users/{id}` | owner only (`#id == authentication.principal.id`) |
| GET | `/api/v1/fleet/count?city=&status=` | authenticated |
| GET | `/api/v1/reservations/availability?startDate=&endDate=&city=` | authenticated, own city only |
| POST | `/api/v1/reservations` | authenticated |
| GET | `/api/v1/reservations/my` | authenticated, paginated |
| POST | `/api/v1/reservations/{id}/confirm` | reservation owner |
| POST | `/api/v1/reservations/{id}/cancel` | reservation owner |
| GET | `/api/v1/admin/users` | ADMIN, paginated |
| POST | `/api/v1/admin/users/{id}/block` | ADMIN |
| POST | `/api/v1/admin/users/{id}/unblock` | ADMIN |
| PATCH | `/api/v1/admin/users/{id}` | ADMIN |
| PATCH | `/api/v1/admin/users/{id}/role` | ADMIN |
| GET | `/api/v1/admin/analytics` | ADMIN |

There is no delete-user endpoint. Admin user management is list, block, unblock, update, change role.

Contract decisions:
- entities are never returned; every response is a DTO record
- list endpoints are wrapped in `PaginatedResponse<T>` with a `data` array and a `meta` object (`currentPage`, `pageSize`, `totalElements`, `totalPages`, `isFirst`, `isLast`, `hasNext`, `hasPrevious`). Clients must read `meta`, not just `data`.
- errors are `application/problem+json` with `status`, `title`, `detail`, `instance`; validation failures add an `invalidFields` map
- PATCH bodies use `JsonNullable<T>` for tri-state semantics: field absent means "leave unchanged", explicit `null` is a validation error
- `/reservations/availability` returns `{ quote, models }`, not a bare array:

```json
{
  "quote": { "rentalDays": 5, "baseDailyRate": 25.00, "effectiveDailyRate": 25.00,
             "discountPercentage": 0, "totalCost": 125.00 },
  "models": [ { "bookableInstanceId": "uuid", "modelName": "...", "modelDescription": "...",
                "modelSpeed": 35, "modelRange": 100, "modelCapacity": 60,
                "modelCategory": "DUAL_BATTERY" } ]
}
```

Numeric specs are JSON numbers. `modelCategory` is a `BikeCategory` name.

Duration is validated on both write paths: `ReservationBookRequest` enforces 3-21 days and strict date order with `@AssertTrue`, and `ReservationController.getAvailableModels` rejects an out-of-range span before quoting, so a client cannot be shown a price for a range it could never book.

## 10. Database

Liquibase changelogs under `db/changelog`, master at `db.changelog-master.yaml`:

| File | Purpose |
|---|---|
| 001-create-users.xml | users table, unique email and phone |
| 002-create-bike-models.xml | bike_models, unique name |
| 003-create-bike-instances.xml | bike_instances + FK to bike_models |
| 004-create-reservations.xml | reservations + FKs + version column |
| 005-add-reservation-exclusion-constraint.xml | btree_gist + `no_overlapping_active_reservations` |
| dev/006-dev-seed.xml | dev-only seed data (context `dev`, `runOnChange="true"`) |
| 007-update-users.xml | adds `last_modified` and `version`, backfills |
| 008-update-users.xml | renames `joined_date` to `created_at`, converts to timestamptz |

The exclusion constraint:

```sql
ALTER TABLE reservations
  ADD CONSTRAINT no_overlapping_active_reservations
  EXCLUDE USING gist (
    bike_instance_id WITH =,
    daterange(start_date, end_date, '[)') WITH &&
  ) WHERE (status IN ('PENDING', 'CONFIRMED'));
```

## 11. ADR reference set

- `docs/adrs/0001-prevent-reservation-race-conditions.md` — PostgreSQL exclusion constraint for overlap prevention. The constraint is the hard guarantee; the service pre-check is UX; `@Version` is for update races only, never for the insert race.
- `docs/adrs/0002-adopt-rich-domain-model-architecture.md` — Rich Domain Model. No public setters on entities, factory methods, invariants in the entity, services orchestrate only.
- `docs/adrs/0003-adopt-project-context-file-with-ai-assistant-grounding.md` — this file, and the AI grounding workflow.

Explicitly rejected anti-patterns: anemic entities with public setters; pessimistic locking for booking; `SERIALIZABLE` + retry; `@Version` as a double-booking defence; `double` for money; returning entities from controllers.

## 12. Testing strategy

Suite composition (`src/test/java`):
- domain unit tests: `ReservationTest` (parameterised transition matrix), `UserTest`
- pricing unit tests: `RentalCostCalculatorTest` with tier boundaries at 7, 8, 14, 15 and 21 days
- service unit tests with Mockito: `ReservationServiceTest`, `UserServiceTest`, `AuthServiceTest`
- security unit tests: `JwtServiceTest` (forgery, expiry), `JwtAuthenticationFilterTest`
- integration tests on Testcontainers (Postgres 16 + Redis 8) via `BaseIntegrationTest`: `ReservationIntegrationTest`, `ReservationConcurrencyIntegrationTest`, `ReservationSchedulerIntegrationTest`, `AuthenticationIntegrationTest`, `UserControllerTest`, `ReservationRepositoryTest`

The concurrency test is the centrepiece: two threads released by a `CountDownLatch` POST the same booking and the suite asserts exactly one 201 and one 409.

Test seams: `SecurityTestHelper.asUser(...)`, `@WithMockCustomUser`, `TestDataFactory`, and a `Clock` bean for time control.

No H2 anywhere. Postgres-specific features are tested on Postgres.

## 13. Known gaps and watchpoints

Accurate as of this update. Do not describe these as working.

**Error contract**
- Unauthenticated requests to protected endpoints return an empty **403**, not a 401. No `AuthenticationEntryPoint` or `AccessDeniedHandler` is configured in `SecurityConfig`.
- `GlobalExceptionHandler` does not extend `ResponseEntityExceptionHandler`, so several client errors fall through to the `Exception` catch-all and return **500**: malformed JSON body, an unknown enum value in a body, a non-UUID path variable, a missing required query parameter, and an unknown enum in a query parameter.
- `ObjectOptimisticLockingFailureException` is imported but has no handler, so update races return 500. This contradicts ADR-001, which promises 409.

**Lifecycle and configuration**
- Both scheduled jobs run at `fixedRate = 10000` with the production cadence commented out. Scheduling is also active during integration tests and can race them.
- `ReservationLifecycleScheduler` uses `Instant.now()` / `LocalDate.now()` directly instead of the injected `Clock`.
- `ReservationService.transitionStatus(...)` is unreferenced by any controller.

**Analytics**
- `findTotalRevenue()` counts CONFIRMED and COMPLETED; `findRevenueTrend()` counts everything except CANCELLED. PENDING is therefore revenue in one chart and not the other.
- `countActiveRentals()` counts all CONFIRMED rows including future bookings, so the derived "occupancy rate" is not occupancy.

**Smaller items**
- `FleetController.getBikesCount` declares `BikeStatus status` without `@RequestParam`; omitting it silently yields 0.
- No index on `reservations(user_id)`, `reservations(status, created_at)` or `bike_instances(city, status)`.
- `dev/006-dev-seed.xml` is `runOnChange="true"` and inserts into `joined_date`, which changeset 008 renamed. Editing the seed will fail against an already-migrated dev database.
- `reservations.created_at` is `TIMESTAMP WITHOUT TIME ZONE` while `users.created_at` is `WITH TIME ZONE`, though both map to `Instant`.
- `pom.xml` still carries empty `<name/>`, `<licenses>`, `<developers>` and `<scm>` blocks from Spring Initializr.

**Missing infrastructure**
- no Dockerfile and no deployed instance
- no Spring Boot Actuator
- no structured logging or MDC request correlation
- CI runs `mvn clean test` only; no coverage, linting, or frontend workflow

## 14. Frontend contract notes

The React client lives in a sibling repository and is a presentation layer only.

- the client role literal is `CLIENT`
- `AdminUserResponse` exposes `createdAt`, not `joinedDate`, and does not include `city`
- money comes from the server quote; the client must not recompute totals
- `endDate` is exclusive in every date calculation
- list responses are enveloped; read `meta` for pagination
- CORS origins come from `cors.allowed-origins` per profile

## 15. Conventions

- issue-driven development; branch per issue, PR closes the issue
- issue and PR templates live in `.github/`
- commit style: `type(#issue): summary`, e.g. `fix: adjust calculator & pricing logic according to github issue (#101)`
- ADRs are added for decisions that would otherwise be re-litigated
- this file is updated when core decisions or contracts change

## 16. Recent merged work

| Commit | Change |
|---|---|
| #101 | `RentalQuote` and `AvailabilityResponse`; availability now returns a server-priced quote alongside models |
| #99 | blocked users lose access immediately; filter reworked to enforce `isAccountNonLocked()` per request and report failures via `handlerExceptionResolver` |
| #97 | entity invariants strengthened; `joined_date` became `created_at` (timestamptz) with `last_modified` and `version` added |
| #95 | package restructure into feature-oriented packages |
| #94 | analytics package and tests |
| #92 | fleet count endpoint |
| #89 | user blocking |
| #87 | reservation cancellation by user |
| #85 | analytics endpoint |
| #83 | reservation constraints |

## 17. Summary

VeloCity is a backend built around one integrity problem: a bike must never be double-booked for overlapping dates, even under concurrency. The exclusion constraint, the rich domain model, the JWT layer and the lifecycle automation all serve that guarantee.

The architecture is settled. The open work is production hardening: the error contract (401 semantics and 4xx mapping), deployment and observability, and tightening the analytics definitions.
