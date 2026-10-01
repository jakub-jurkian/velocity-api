# VeloCity Fleet API — Project Context

Last updated: 2026-09-30

This file is the single source of truth for the project. It reflects the repo state, the architectural decisions captured in ADRs, and the current implementation status for the backend service that powers the VeloCity frontend.

Per ADR-003 this file is living documentation: it is updated at the end of each closed issue, not retroactively. If something here contradicts the code, the code wins and this file is the bug.

## 0. Where the code actually is

**`main` @ `24bd1ff`** (= `origin/main`) — everything through PR #130. Everything below describes `main`.

#121-#129 are the fixes from the 2026-09-24 review (login email, reservation expiry and city check, test infrastructure, API contract, admin bike endpoints). They were merged as a stack; squash-merging it dropped changes during conflict resolution and broke the build from #125 to #129, and #130 restored the tested state. See section 15 for the merge rule this led to.

**Deployed.** The `prod` profile runs on an Oracle Cloud (OCI) ARM VM behind Caddy:
- API: `https://api.velocityfleet.dev` (Swagger UI at `/api/swagger-ui.html`)
- frontend: `https://www.velocityfleet.dev` (Vercel, repo `velocity-client`)

Every push to `main` builds a `linux/arm64` image, pushes it to GHCR, and redeploys over SSH (`.github/workflows/deploy.yml`). The deploy job only runs if the image builds, so a commit that does not compile never reaches the server. The live instance runs #130.

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

Feature-complete for the demo flow and deployed. The error contract is settled, the concurrency guarantee is in place and proven by test, and the domain model enforces its own invariants. Most defects from the 2026-09-24 review are fixed (#121-#130); the remaining ones are listed in section 13.

Implemented on `main`:
- JWT authentication with per-request account-status enforcement
- single-parse JWT verification; tokens carry a `jti` and the Redis blacklist keys on it
- user registration, login, logout, profile updates, admin user lifecycle
- fleet counts and date-range availability with server-side pricing
- reservation creation, ownership checks, lifecycle transitions, scheduled jobs
- reservation expiry after a 30-minute confirmation window, and bookings restricted to the client's own city
- admin bike-status changes with optimistic locking and a conflict guard (section 8a)
- allow-listed sorting with default orders on every paginated endpoint
- admin analytics aggregates
- ProblemDetail error handling, including 401/403 semantics and framework-level 4xx mapping
- Postgres-backed reservation integrity via an exclusion constraint
- `PricingProperties` as a validated `@ConfigurationProperties` record
- Actuator health endpoint (`/actuator/health`, details hidden)
- multi-stage Dockerfile (non-root runtime user, `prod` profile pinned), production Compose stack, Caddy reverse proxy
- a `prod`-context demo seed that re-anchors itself to the current date on every boot (see DEMO.md)
- concurrency, integration, repository and domain tests on Testcontainers (101 tests, green)

Not implemented:
- structured logging, request/correlation IDs
- rate limiting on the auth endpoints
- refresh tokens
- tests for the admin business logic (`AdminUserService`, `FleetService.updateBikeStatus`), `BikeInstance.transitionTo`, `AnalyticsService`, `cancelReservation`, logout revocation, and the availability query

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
- Spring Boot Actuator (health only)
- Docker: `docker-compose.yaml` runs Postgres + Redis for local dev; `docker-compose.prod.yaml` runs the API image, Postgres, Redis and Caddy in production
- GitHub Actions: `ci.yml` (tests on push/PR), `deploy.yml` (build arm64 image → GHCR → SSH deploy to OCI)

Runtime conventions:
- `spring.profiles.default: dev`, so `./mvnw spring-boot:run` works with no extra flags. A deployment must set `SPRING_PROFILES_ACTIVE=prod` explicitly; with it unset the app falls back to `dev` and dies against `localhost:5432` with a misleading connection error.
- `spring.web.locale: en`, so Bean Validation messages are English regardless of JVM locale
- PostgreSQL on localhost:5432 and Redis on localhost:6379 in local dev
- schema changes are Liquibase-managed
- `open-in-view: false`; no lazy loading outside a transaction
- security is stateless with JWT-based filtering
- Redis repository scanning is disabled (`spring.data.redis.repositories.enabled: false`)
- pricing is BigDecimal-based, bound by `PricingProperties` from the top-level `pricing.*` prefix (NOT `spring.pricing.*`)
- configuration properties classes are discovered via `@ConfigurationPropertiesScan` on the main class

Local build note: `JAVA_HOME` must point at an installed JDK 25. A stale value (e.g. a JDK that a patch update replaced) fails with "The JAVA_HOME environment variable is not defined correctly" before Maven starts.

## 4. Architecture and package structure

```
com.velocity.api
├── analytics    controller, service, dto
├── auth         controller, service, dto
├── bike         entities, controller (FleetController, AdminFleetController), service (FleetService),
│                repository (+projections), dto, exception
├── common       City, JsonNullables, dto (PaginatedResponse), web (SortableFields),
│                exception (GlobalExceptionHandler, ResourceNotFoundException,
│                DomainValidationException, InvalidSortException)
├── config       ClockConfig, OpenApiConfig, SecurityConfig, SchedulingConfig
├── pricing      RentalCostCalculator, PricingProperties, dto (RentalQuote)
├── reservation  entities (Reservation, RentalPeriod), controller, service, repository (+projections),
│                dto, mapper, scheduler, exception
├── security     CustomUserDetails(+Service), JwtService, JwtAuthenticationFilter,
│                DelegatingAuthenticationEntryPoint, DelegatingAccessDeniedHandler,
│                repository (TokenBlacklistRepository)
└── user         entity, controller (User + AdminUser), service, repository, dto, exception
```

Admin endpoints are guarded twice: `@PreAuthorize("hasRole('ADMIN')")` on the controller class and on the service methods, so a new endpoint that calls an admin service cannot skip the check.

Architectural rules visible in the codebase:
- controllers are thin HTTP adapters
- services orchestrate transactions and business rules
- repositories handle data access
- entities protect their own invariants
- DTOs are used at the API boundary; entities never leak to the web layer
- global error handling returns RFC 9457 (formerly RFC 7807) ProblemDetail payloads
- method security enforces owner and admin use cases

## 5. Domain model

### User
Fields: `id` (UUID), `email`, `passwordHash`, `fullName`, `phone`, `status`, `role`, `city`, `createdAt` (Instant, `@CreatedDate`, immutable), `lastModified` (Instant, `@LastModifiedDate`), `version` (`@Version`, private).

Rules:
- unique email and phone (DB constraints `UC_USERSEMAIL_COL` and `UC_USERSPHONE_COL`)
- created via `User.registerClient(...)`; constructor is private
- mutation only via `updateProfile(...)`, `updateProfileByAdmin(...)`, `changeRoleByAdmin(...)`, `block()`, `unblock()`
- email is normalised to lowercase and regex-validated inside the entity
- phone must match E.164 with a length floor: `^\+[1-9]\d{7,14}$` (8-15 digits after the `+`)
- `block()` on an already-blocked user and `unblock()` on an already-active user throw `InvalidUserStateException` (422)
- auditing is enabled by `@EnableJpaAuditing` on the main class

Note: there is no `softDelete()` and no `DELETED` status. `UserStatus` is `ACTIVE, BLOCKED` only.

### BikeModel
Fields: `id`, `name` (unique), `description`, `speed`, `range`, `capacity`, `category`.
Created via `BikeModel.create(...)`. Ranges validated in the entity (speed 1-45, range 15-500, capacity 1-100). Violations throw `DomainValidationException`.

### BikeInstance
Fields: `id`, `status`, `city`, `bikeModel` (LAZY `@ManyToOne`), `version` (`@Version`, private).
`BikeStatus`: ACTIVE, MAINTENANCE, LOST, RETIRED. Created via `BikeInstance.initialize(...)`, defaults to ACTIVE. `assertBookableIn(clientCity)` allows booking only an ACTIVE bike in the client's own city (`InvalidBikeStateException` / `BikeInOtherCityException`, both 422). `transitionTo(...)` refuses to move a RETIRED bike and forces a LOST bike through MAINTENANCE first.

### Reservation
Fields: `id`, `startDate`, `endDate`, `totalCost` (BigDecimal), `status`, `cancellationReason` (nullable String), `createdAt` (Instant, `@CreatedDate`), `user` (LAZY), `bikeInstance` (LAZY), `version` (`@Version`).

`ReservationStatus`: PENDING, CONFIRMED, COMPLETED, CANCELLED.

Rules:
- created via `Reservation.book(user, bike, RentalPeriod, currentDate, totalCost)`; start date must be strictly in the future
- `RentalPeriod(startDate, endDate)` is the single home of the date rules: both dates required, end after start, 3-21 days (`MIN_DAYS`/`MAX_DAYS`). The booking request's `@AssertTrue` checks, the availability endpoint and the entity all use it
- null arguments are rejected before any value-based validation runs
- state machine enforced by `Reservation.transitionTo(newStatus, currentDate)`; same-status transition is a deliberate no-op
- cancelling through `transitionTo` on or after `startDate` throws `LateCancelException` (422)
- invalid transitions throw `InvalidStatusTransitionException` (422)
- `confirm(now)` is the customer's confirmation: refused with `ReservationExpiredException` (422) once `CONFIRMATION_WINDOW` (30 minutes from `createdAt`) has passed, even if the scheduler has not expired the row yet
- `expire(now)` is the system path for unconfirmed bookings: skips the late-cancel rule, records the reason `"Not confirmed within 30 minutes"`, and is a no-op if the reservation already left PENDING
- `cancelByOperator(reason)` is the operator path: it requires a non-blank reason, skips the late-cancel rule (so a rental already under way can be cancelled), is a no-op on an already-cancelled reservation, and refuses a COMPLETED one
- `cancellationReason` is written only by `expire` and `cancelByOperator`; a customer's own cancellation leaves it null

### Enums shared
`City`: GDANSK, WROCLAW, WARSAW, POZNAN. `UserRole`: CLIENT, ADMIN. `BikeCategory`: AGILITY, HEAVY_DUTY, DUAL_BATTERY.

Important for the frontend: the client role is **CLIENT**, not `USER`.

## 6. Pricing

`RentalCostCalculator.calculateQuote(int rentalDays)` returns a `RentalQuote` record and is the only place money is computed. The daily rate is injected as `PricingProperties`, a `@Validated @ConfigurationProperties(prefix = "pricing")` record, so a negative or missing rate fails at startup rather than at first request.

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
- `JwtService` decodes the signing key and builds the `JwtParser` once in its constructor and exposes a single `parseClaims(...)`. A request therefore verifies the signature exactly once.
- token claims: subject is the email, plus a random `jti`. There are no `userId` or `role` claims — authorities and account status are re-read from the database every request. Using the (mutable) email as the subject is an open defect, see section 13.
- `JwtAuthenticationFilter`: extracts the bearer token, verifies it, checks the `jti` against the Redis blacklist, loads `UserDetails`, and refuses blocked accounts via `isAccountNonLocked()`. Failures are handed to the `handlerExceptionResolver`, so they surface as ProblemDetail from `GlobalExceptionHandler` rather than as raw servlet errors.
- `DelegatingAuthenticationEntryPoint` and `DelegatingAccessDeniedHandler` route unauthenticated and forbidden requests through the same resolver, so a missing token returns **401** and an insufficient role returns **403**, both as ProblemDetail.
- `CustomUserDetailsService` resolves authorities as `ROLE_<UserRole>` from persisted data
- BCrypt password hashing
- On logout, Redis blacklists the token's `jti` for its remaining TTL
- invalid, expired or revoked tokens raise `InvalidTokenException` (401 "session no longer valid"), kept separate from `BadCredentialsException`, which only the login path raises
- Redis has 200 ms command/connect timeouts. The blacklist **read** fails open (logged, request proceeds); the blacklist **write** on logout fails loudly with `TokenRevocationUnavailableException` (**503**), so a logout that could not be recorded is not reported as a success
- Redis is excluded from `/actuator/health` (`management.health.redis.enabled: false`), consistent with failing open: a Redis outage does not mark the API as down
- the login response's `expiresIn` is in seconds

Because authorities and account status are re-read from the database on every request, blocking a user takes effect immediately rather than when their token expires.

Password policy: `@Size(min = 8, max = 64)` plus a composition rule (upper, lower, digit, special character), mirrored in the frontend's `validators.ts`. There is no password-change endpoint. The token TTL is 24 hours and there are no refresh tokens.

Public endpoints: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, and the Swagger/OpenAPI paths. Everything else requires authentication.

## 8. Reservation logic and lifecycle

State machine:
- PENDING -> CONFIRMED
- PENDING -> CANCELLED
- CONFIRMED -> COMPLETED
- CONFIRMED -> CANCELLED
- CANCELLED and COMPLETED are terminal

Automation (`ReservationLifecycleScheduler`):
- PENDING reservations at least `Reservation.CONFIRMATION_WINDOW` (30 minutes) old are expired through `Reservation.expire`, not the customer cancel path, so a booking that goes stale after midnight on its own start date is still released; cadence from `scheduling.pending-cadence` (default `PT5M`)
- CONFIRMED reservations whose `endDate` has passed are auto-completed; schedule from `scheduling.reservation.completion-cron` (default `0 0 1 * * ?`)
- both jobs read time from the injected `Clock`
- each id is processed in its own transaction; `ObjectOptimisticLockingFailureException` is caught per item and logged so one conflict cannot abort the batch
- scheduling is gated by `@ConditionalOnProperty("scheduling.enabled")` on `SchedulingConfig` and switched off in the test profile so it cannot race integration tests

Booking path (`ReservationService.book`): load user and bike, `bike.assertBookableIn(user.getCity())`, run a service-level availability pre-check, price the rental from `RentalPeriod.days()`, then save. The pre-check is UX only; the database constraint is the guarantee. `POST /reservations` returns 201 with a `Location` header pointing at `GET /reservations/{id}`.

Ownership: `getUserReservation`, `confirmReservation` and `cancelReservation` load via `findByIdAndUserId(...)`, so a reservation belonging to another user returns **404** rather than confirming the row exists.

## 8a. Admin bike-status conflict guard (#115, moved to the bike package in #129)

Taking a bike out of service used to silently strand whatever reservations were on it. This closes that.

- `FleetService.updateBikeStatus(bikeId, status, version, force)` (called from `AdminFleetController`, `@PreAuthorize("hasRole('ADMIN')")` on both the controller and the service method) first compares the `version` sent by the client with the loaded bike; a mismatch throws `OptimisticLockingFailureException` (**409**, handler registered on the parent type so both the hand-thrown and ORM variants match).
- `ReservationRepository.findActiveConflictsForBike(bikeId, currentDate)` returns PENDING or CONFIRMED reservations on that bike whose `endDate` is still in the future, with the user `JOIN FETCH`ed so the email is available without a lazy load. It runs whenever the target status is not ACTIVE.
  - `force=false` (default): throws `BikeUnderActiveRentalException` carrying a `List<ConflictDto>`.
  - `force=true`: cancels each conflict through `Reservation.cancelByOperator` with the reason `"Cancelled by Admin: Bike transitioned to <status>"`, then applies the status change. `cancelByOperator` skips the late-cancel rule, so rentals already under way can be cancelled — which is the case this path exists for.
- `ConflictDto(UUID reservationId, String userEmail, LocalDate startDate, LocalDate endDate)`.
- `GlobalExceptionHandler` maps `BikeUnderActiveRentalException` to **409** with the conflict list attached as a `conflicts` property on the ProblemDetail, so the admin UI can list exactly what it is about to cancel.
- `PATCH /api/v1/admin/bikes/{id}/status` takes `?force=true|false`, defaulting to false.
- Changelog `012` adds a nullable `cancellation_reason VARCHAR(255)` to `reservations`.

Access control is covered by `SecurityAccessIntegrationTest` (CLIENT → 403, bike unchanged). Still open: the service logic (version mismatch, conflicts, `force`) has no tests, and a status change can race a concurrent booking (section 13).

## 9. API surface

| Method | Path | Access |
|---|---|---|
| POST | `/api/v1/auth/register` | public |
| POST | `/api/v1/auth/login` | public |
| POST | `/api/v1/auth/logout` | authenticated |
| GET | `/api/v1/auth/me` | authenticated |
| PATCH | `/api/v1/users/{id}` | owner only (`#id == authentication.principal.id`) |
| GET | `/api/v1/fleet/count?city=&status=` | authenticated |
| GET | `/api/v1/reservations/availability?startDate=&endDate=` | authenticated; city derived from the principal |
| POST | `/api/v1/reservations` | authenticated |
| GET | `/api/v1/reservations/my` | authenticated, paginated |
| GET | `/api/v1/reservations/{id}` | reservation owner |
| POST | `/api/v1/reservations/{id}/confirm` | reservation owner |
| POST | `/api/v1/reservations/{id}/cancel` | reservation owner |
| GET | `/api/v1/admin/users` | ADMIN, paginated |
| POST | `/api/v1/admin/users/{id}/block` | ADMIN |
| POST | `/api/v1/admin/users/{id}/unblock` | ADMIN |
| PATCH | `/api/v1/admin/users/{id}` | ADMIN |
| PATCH | `/api/v1/admin/users/{id}/role` | ADMIN |
| GET | `/api/v1/admin/bikes` | ADMIN, paginated, optional `?status=` |
| PATCH | `/api/v1/admin/bikes/{id}/status?force=` | ADMIN, optimistic-locked via a `version` in the body |
| GET | `/api/v1/admin/analytics` | ADMIN |

Swagger UI is served at `/api/swagger-ui.html` and the OpenAPI document at `/api/api-docs`.

There is no delete-user endpoint. Admin user management is list, block, unblock, update, change role.

Contract decisions:
- entities are never returned; every response is a DTO record
- list endpoints are wrapped in `PaginatedResponse<T>` with a `data` array and a `meta` object (`currentPage`, `pageSize`, `totalElements`, `totalPages`, `isFirst`, `isLast`, `hasNext`, `hasPrevious`). Clients must read `meta`, not just `data`.
- errors are `application/problem+json` with `status`, `title`, `detail`, `instance`; validation failures add an `invalidFields` map, and a bike-status conflict adds a `conflicts` array
- PATCH bodies use `JsonNullable<T>` for tri-state semantics: field absent means "leave unchanged", explicit `null` is a validation error
- paginated endpoints accept only an allow-listed `?sort=` (`common/web/SortableFields`); anything else is **400 Invalid Sort Parameter**. Defaults: `/reservations/my` by `startDate` desc, `/admin/users` by `createdAt` desc, the bike list by `id`
- `@Future`/`@Past` read the application `Clock` (a `ValidationConfigurationCustomizer` in `ClockConfig`), so validation and the domain agree on "today"
- duplicate email and phone are pre-checked in the service layer and return **409**; if a race slips past the pre-check the constraint mapping produces the same status
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

Duration is validated on both paths through `RentalPeriod`: `ReservationBookRequest` reports it per field (400 with `invalidFields`), and `getAvailableModels` builds a `RentalPeriod` before quoting (422), so a client cannot be shown a price for a range it could never book.

## 10. Database

Liquibase changelogs under `db/changelog`, master at `db.changelog-master.yaml`:

| File | Purpose |
|---|---|
| 001-create-users.xml | users table, unique email and phone |
| 002-create-bike-models.xml | bike_models, unique name |
| 003-create-bike-instances.xml | bike_instances + FK to bike_models |
| 004-create-reservations.xml | reservations + FKs + version column |
| 005-add-reservation-exclusion-constraint.xml | btree_gist + `no_overlapping_active_reservations` |
| 007-update-users.xml | adds `last_modified` and `version`, backfills |
| 008-update-users.xml | renames `joined_date` to `created_at`, converts to timestamptz |
| 009-add-bike-instance-version.xml | `version` column on bike_instances |
| 010-align-reservation-created-at-tz.xml | `reservations.created_at` to timestamptz, matching users |
| 011-add-indexes.xml | `reservations(user_id)`, `reservations(status, created_at)`, `bike_instances(city, status)` |
| 012-add-cancellation-reason-for-reservations.xml | nullable `cancellation_reason` on reservations |
| dev/999-dev-seed.xml | dev-only seed data (context `dev`) |
| prod/001-prod-seed.xml | public demo data (context `prod`) — bike models once; accounts, fleet and reservations `runAlways` |

There is no `006`; the original seed changelog was renumbered to `dev/999` so seeds always run last.

Both seeds are context-gated: `application-dev.yml` runs `dev`, `application-prod.yml` runs `prod`, the test profile runs `test` (neither seed). The prod seed upserts the demo accounts and fleet and **deletes and rebuilds every reservation on each boot**, re-anchored to `CURRENT_DATE`, so the demo never goes stale. It must never be pointed at a database holding real bookings. DEMO.md documents the accounts and the walkthrough.

`src/main/resources/liquibase.yml` configures the **Maven plugin only** (local `diff` / `generateChangeLog` work against the local dev database) and is not read by the running application.

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
- `docs/adrs/0002-adopt-rich-domain-model-architecture.md` — Rich Domain Model. No public setters on entities, factory methods, invariants in the entity, services orchestrate only. Bookability lives in `BikeInstance.assertBookableIn`, the date rules in `RentalPeriod`.
- `docs/adrs/0003-adopt-project-context-file-with-ai-assistant-grounding.md` — this file, and the AI grounding workflow.

Explicitly rejected anti-patterns: anemic entities with public setters; pessimistic locking for booking; `SERIALIZABLE` + retry; `@Version` as a double-booking defence; `double` for money; returning entities from controllers.

## 12. Testing strategy

101 tests, green on a clean build, in about a minute.

- domain unit tests: `ReservationTest` (transition matrix, confirm/expire windows), `RentalPeriodTest`, `BikeInstanceTest`, `UserTest`
- pricing unit tests: `RentalCostCalculatorTest` with tier boundaries at 7, 8, 14, 15 and 21 days
- service unit tests with Mockito: `ReservationServiceTest`, `UserServiceTest`, `AuthServiceTest`
- security unit tests: `JwtServiceTest` (forgery, expiry, unique jti), `JwtAuthenticationFilterTest`
- full-application tests extending `AbstractApiIntegrationTest`: `ReservationIntegrationTest`, `ReservationConcurrencyIntegrationTest`, `ReservationSchedulerIntegrationTest` (including the midnight expiry case), `SecurityAccessIntegrationTest` (401/403/200 matrix including the admin bike endpoints, sort allow-list), `UserControllerTest`, `VelocityApiApplicationTests`
- repository slice test: `ReservationRepositoryTest` (`@DataJpaTest`)

The concurrency test is the centrepiece: two threads released by a `CountDownLatch` POST the same booking and the suite asserts exactly one 201 and one 409.

That test has **two** valid outcomes, and both must map to 409. Roughly two runs in three the exclusion constraint fires; the remaining third Postgres reports `deadlock detected`, because two concurrent inserts contending on a GiST exclusion constraint for the same key genuinely deadlock. Because of this the test asserts status codes only — asserting an exact body would make it flaky, since the two paths produce different messages. A separate sequential test is the right place to pin the constraint message.

Test seams: `SecurityTestHelper.asUser(id, role)`, `TestDataFactory`, and `MutableClock` (`support/`), a `@Primary` test clock that follows real time until a test fixes it with `clock.setInstant(...)`.

No H2 anywhere. Postgres-specific features are tested on Postgres.

Containers and contexts: `BaseIntegrationTest` starts Postgres and Redis once per test run (static initializer + `@ServiceConnection`; deliberately not `@Testcontainers`/`@Container`, which restart them per class and break Spring's context cache). Every full-application test extends `AbstractApiIntegrationTest`, which fixes one configuration (`RANDOM_PORT`, MockMvc, TestRestTemplate, test clock, data factory), so the suite builds **two** Spring contexts: that one and the `@DataJpaTest` slice. Do not add `@MockitoBean`, `@Import` or properties to a subclass; each creates another context. The shared database is truncated after every test.

## 13. Known gaps and watchpoints

Accurate as of the 2026-09-24 review, updated 2026-09-30 after #121-#130. Items marked *reproduced* were confirmed against a running instance or the test suite. Do not describe these as working.

**Defects — reproduced**
- The JWT subject is the email, which admins can change. After an admin renames a user, a new registration with the old email makes the old token resolve to the new account. The subject should be the user's UUID.
- An invalid or expired Bearer header on a public endpoint (e.g. `/auth/login`) returns 401 instead of being ignored. The frontend avoids it by sending `token: null` on login and register.

**Defects — by analysis**
- A bike-status change can race a booking: the admin path finds no conflicts while a concurrent booking inserts a reservation on the still-ACTIVE bike, and both commit. The exclusion constraint cannot cover this invariant because it spans two tables. Fix direction: `PESSIMISTIC_READ` on the bike in `book`, `PESSIMISTIC_WRITE` in `updateBikeStatus`, then an ADR-004.

**Public demo**
- The demo admin credentials are published (prod seed + DEMO.md). Anyone can list the email and phone of every visitor who registered, edit or block them, and use `ops@` to demote or block `admin@` until the next restart. Options: mask contact data of non-seed accounts in the prod profile, purge visitor accounts nightly, refuse admin changes to seed accounts.
- Swagger UI and the OpenAPI document are public in production (deliberate for a portfolio).

**Design and code quality**
- `GlobalExceptionHandler` has ~20 near-identical handlers; a base exception carrying status, title and a stable error code would collapse them.
- The E.164 phone pattern is duplicated in four backend locations (`User.PHONE_PATTERN`, `UserRegistrationRequest`, `UserProfileUpdateRequest`, `AdminUserUpdateRequest`) plus the frontend.

**Security**
- No rate limiting on `/api/v1/auth/register` or `/api/v1/auth/login`.
- No password-change endpoint; 24-hour tokens with no refresh token.
- Redis blacklist reads fail open (deliberate; see section 7).

**Observability**
- No structured logging and no request/correlation ID. `AuthService.register` logs the user's email.

**Testing**
- The admin business logic has no tests: block/unblock, self-demotion, role change, update conflicts (`AdminUserService`), and the bike-status path (version mismatch, 409 conflicts, `force`) in `FleetService`. Only access control is tested.
- Also untested: `BikeInstance.transitionTo`, `AnalyticsService`, `cancelReservation`, the `findAvailableModels` native query, logout followed by a rejected token, and the `invalidFields` response shape.
- Dev-seed and test dates are hardcoded in 2026 and have begun to fall into the past.

**CI**
- `ci.yml` runs `mvn clean test` rather than `./mvnw -B verify`; no coverage report, no test-report upload on failure, no Dependabot.

## 14. Frontend contract notes

The React client lives in a sibling repository and is a presentation layer only.

- the client role literal is `CLIENT`
- `AdminUserResponse` exposes `createdAt` (not `joinedDate`) and includes `city`, `status` and `role`
- `BikeInstanceResponse` (admin bike list) includes `version`, which the client must send back on a status change
- money comes from the server quote; the client must not recompute totals
- `endDate` is exclusive in every date calculation
- list responses are enveloped; read `meta` for pagination
- CORS origins come from `cors.allowed-origins` per profile
- the phone pattern `^\+[1-9]\d{7,14}$` is mirrored in `src/utils/validators.ts` and must be changed in both repositories together
- on a 400 the client reads per-field messages from `invalidFields` and renders them against the inputs; the top-level `detail` is only a generic summary
- a duplicate email or phone returns 409 and is shown on the offending field rather than as a toast
- a 409 from the bike-status endpoint carries a `conflicts` array the admin UI can render before offering `?force=true`

## 15. Conventions

- issue-driven development; branch per issue, PR closes the issue
- issue and PR templates live in `.github/`
- commit style: `type(#issue): summary`, e.g. `fix: adjust calculator & pricing logic according to github issue (#101)`
- ADRs are added for decisions that would otherwise be re-litigated
- this file is updated when core decisions or contracts change
- stacked PRs (a branch built on another unmerged branch) are merged with a merge commit, or rebased onto the new `main` and re-tested before a squash merge. Squashing a stack without that is what broke `main` between #125 and #130.

## 16. Recent merged work

| PR | Change |
|---|---|
| #130 | restores six files that lost changes while the #123-#129 stack was squash-merged; `main` compiles and deploys again |
| #129 | `AdminFleetController` (`/api/v1/admin/bikes`) and `FleetService` take over bike admin from the `user` package; ADMIN guard on controller and service; bike DTOs in `bike/dto` |
| #127 | sort allow-lists (`SortableFields`) and default sorts; Redis excluded from health; logout 503 when Redis is down; `expiresIn` in seconds; `Location` header and `GET /reservations/{id}`; `@Future` on the application clock; code cleanups |
| #125 | singleton Testcontainers, `AbstractApiIntegrationTest`, `MutableClock`; 6 → 2 Spring contexts; `SecurityAccessIntegrationTest` replaces the helper-only auth test |
| #123 | `Reservation.expire`/`confirm` with a 30-minute window, `RentalPeriod`, `BikeInstance.assertBookableIn` (status + city) |
| #121 | login normalises the email (trim + lowercase) |
| #119 | README and DEMO.md rewritten for the deployed state |
| — (`58865fc`) | CD pipeline: `deploy.yml` builds an arm64 image to GHCR and deploys to the OCI VM over SSH; `docker-compose.prod.yaml`; Caddy reverse proxy with HTTPS; local `docker-compose.yaml` reduced to Postgres + Redis |
| #118 | `InvalidTokenException` separates expired/revoked tokens from wrong passwords; Dockerfile (multi-stage, non-root, `prod` pinned); Actuator health; prod demo seed; DEMO.md |
| #116 | pre-deploy hardening: `ddl-auto: validate` at the right key, Europe/Warsaw `Clock`, Redis timeouts and fail-open reads, unmapped integrity violations → 500, 23P01 mapping, `CannotAcquireLockException` handler, password composition rule; README and this file realigned |
| #115 | admin bike-status conflict guard with `?force`, `cancelByOperator`, `cancellation_reason` column |
| #113 | single-parse JWT with a `jti`-keyed blacklist; `IllegalArgumentException` replaced by `DomainValidationException` across the domain; `PricingProperties`; `Reservation` null-check ordering fix; `@RequestParam` on the fleet status filter; descriptive Liquibase changeset ids; `runOnChange` removed from the dev seed |
| #111 | indexes on `reservations(user_id)`, `reservations(status, created_at)`, `bike_instances(city, status)` |
| #101 | `RentalQuote` and `AvailabilityResponse`; availability now returns a server-priced quote alongside models |
| #99 | blocked users lose access immediately; filter reworked to enforce `isAccountNonLocked()` per request and report failures via `handlerExceptionResolver` |
| #97 | entity invariants strengthened; `joined_date` became `created_at` (timestamptz) with `last_modified` and `version` added |
| #95 | package restructure into feature-oriented packages |
| #94 | analytics package and tests |
| #92 | fleet count endpoint |
| #89 | user blocking |
| #87 | reservation cancellation by user |

## 17. Summary

VeloCity is a backend built around one integrity problem: a bike must never be double-booked for overlapping dates, even under concurrency. The exclusion constraint, the rich domain model, the JWT layer and the lifecycle automation all serve that guarantee.

The architecture is settled, the error contract is largely complete, and the app is deployed with a self-resetting demo. The open work is, in order: the rest of the auth fixes (the email-based JWT subject, tokens on public endpoints), the bike-status/booking race together with the admin business-logic tests, CI, and the demo's exposure of visitor data.
