# VeloCity Fleet API — Project Context

Last updated: 2026-09-21

This file is the single source of truth for the project. It reflects the repo state, the architectural decisions captured in ADRs, and the current implementation status for the backend service that powers the VeloCity frontend.

Per ADR-003 this file is living documentation: it is updated at the end of each closed issue, not retroactively. If something here contradicts the code, the code wins and this file is the bug.

## 0. Where the code actually is

Reading this section first will save confusion, because the work is currently spread across three places.

**`main` @ `fc211bb`** (= `origin/main`) — everything through PR #113. This is the baseline everything below describes unless stated otherwise.

**Uncommitted on `main`** — the admin bike-status conflict guard and reservation cancellation reasons. Described in section 8a. Compiles and the suite passes; it has no tests of its own.

**`chore/pre-deploy-hardening` @ `0562dd6`, not merged** — a batch of pre-deploy fixes that are therefore **not** true of `main`. Until this lands, `main` still has all of the following:
- `ddl-auto: validate` sitting under `spring.jpa.properties.hibernate`, where it is silently ignored — there is no schema validation at startup
- `Clock.systemDefaultZone()`, so date boundaries follow the host timezone
- no Redis command/connect timeouts and no failure handling on the blacklist read
- an unrecognised `DataIntegrityViolationException` mapping to 409 rather than a logged 500
- no SQLState `23P01` fallback in `extractConstraintName`, so the `no_overlapping_active_reservations` mapping never actually matches and the overlap 409 comes from the blanket default
- no `CannotAcquireLockException` handler
- `BadCredentialsException` echoing Spring's literal `"Bad credentials"` string to the client
- registration reporting a different duplicate-email message than the constraint mapping
- no composition rule on `UserRegistrationRequest.password`
- the unused `pricing/exception/InvalidRentalDurationException`
- a plaintext password in `src/main/resources/liquibase.yml`

That branch also carries the previous rewrite of this document.

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

Feature-complete for the demo flow. The error contract is settled, the concurrency guarantee is in place and proven by test, and the domain model enforces its own invariants. Nothing is deployed.

Implemented on `main`:
- JWT authentication with per-request account-status enforcement
- single-parse JWT verification; tokens carry a `jti` and the Redis blacklist keys on it
- user registration, login, logout, profile updates, admin user lifecycle
- fleet counts and date-range availability with server-side pricing
- reservation creation, ownership checks, lifecycle transitions, scheduled jobs
- admin analytics aggregates
- ProblemDetail error handling, including 401/403 semantics and framework-level 4xx mapping
- Postgres-backed reservation integrity via an exclusion constraint
- `PricingProperties` as a validated `@ConfigurationProperties` record
- concurrency, integration, repository and domain tests on Testcontainers (61 tests, green)

Not implemented:
- Dockerfile, deployment, Actuator, health endpoint, structured logging
- rate limiting on the auth endpoints
- a production seed — see section 13, this blocks a usable deploy
- tests for `AdminUserService`, `AnalyticsService`, `cancelReservation`, and the availability query

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
- Docker Compose (Postgres + Redis only; the app itself is not containerised)
- GitHub Actions

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
├── bike         entities, controller, service, repository (+projection), dto, exception
├── common       City, JsonNullables, dto (PaginatedResponse), exception (GlobalExceptionHandler,
│                ResourceNotFoundException, DomainValidationException)
├── config       ClockConfig, OpenApiConfig, SecurityConfig, SchedulingConfig
├── pricing      RentalCostCalculator, PricingProperties, dto (RentalQuote), exception
├── reservation  entity, controller, service, repository (+projections), dto, mapper, scheduler, exception
├── security     CustomUserDetails(+Service), JwtService, JwtAuthenticationFilter,
│                DelegatingAuthenticationEntryPoint, DelegatingAccessDeniedHandler,
│                repository (TokenBlacklistRepository)
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
`BikeStatus`: ACTIVE, MAINTENANCE, LOST, RETIRED. Created via `BikeInstance.initialize(...)`, defaults to ACTIVE. Booking is only allowed when ACTIVE. `transitionTo(...)` refuses to move a RETIRED bike and forces a LOST bike through MAINTENANCE first.

### Reservation
Fields: `id`, `startDate`, `endDate`, `totalCost` (BigDecimal), `status`, `cancellationReason` (nullable String, uncommitted), `createdAt` (Instant, `@CreatedDate`), `user` (LAZY), `bikeInstance` (LAZY), `version` (`@Version`).

`ReservationStatus`: PENDING, CONFIRMED, COMPLETED, CANCELLED.

Rules:
- created via `Reservation.book(...)`; duration must be 3-21 days; start date must be strictly in the future
- null arguments are rejected before any value-based validation runs
- lifecycle enforced by `Reservation.transitionTo(newStatus, currentDate)`, with an overload taking a cancellation reason
- a cancellation reason is only accepted alongside a transition to CANCELLED; anything else throws `DomainValidationException`
- same-status transition is a deliberate no-op
- cancelling on or after `startDate` throws `LateCancelException` (422) — this applies to the admin force-cancel path too, see section 13
- invalid transitions throw `InvalidStatusTransitionException` (422)

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
- token claims: subject is the email, plus a random `jti`. There are no `userId` or `role` claims — authorities and account status are re-read from the database every request.
- `JwtAuthenticationFilter`: extracts the bearer token, verifies it, checks the `jti` against the Redis blacklist, loads `UserDetails`, and refuses blocked accounts via `isAccountNonLocked()`. Failures are handed to the `handlerExceptionResolver`, so they surface as ProblemDetail from `GlobalExceptionHandler` rather than as raw servlet errors.
- `DelegatingAuthenticationEntryPoint` and `DelegatingAccessDeniedHandler` route unauthenticated and forbidden requests through the same resolver, so a missing token returns **401** and an insufficient role returns **403**, both as ProblemDetail.
- `CustomUserDetailsService` resolves authorities as `ROLE_<UserRole>` from persisted data
- BCrypt password hashing
- On logout, Redis blacklists the token's `jti` for its remaining TTL

Because authorities and account status are re-read from the database on every request, blocking a user takes effect immediately rather than when their token expires.

Password policy on `main` is length only: `@Size(min = 8, max = 64)`. There is no composition rule and no password-change endpoint. The token TTL is 24 hours.

Public endpoints: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, and the Swagger/OpenAPI paths. Everything else requires authentication.

## 8. Reservation logic and lifecycle

State machine:
- PENDING -> CONFIRMED
- PENDING -> CANCELLED
- CONFIRMED -> COMPLETED
- CONFIRMED -> CANCELLED
- CANCELLED and COMPLETED are terminal

Automation (`ReservationLifecycleScheduler`):
- stale PENDING reservations older than 30 minutes are auto-cancelled; cadence from `scheduling.pending-cadence` (default `PT5M`)
- CONFIRMED reservations whose `endDate` has passed are auto-completed; schedule from `scheduling.reservation.completion-cron` (default `0 0 1 * * ?`)
- both jobs read time from the injected `Clock`
- each id is processed in its own transaction; `ObjectOptimisticLockingFailureException` is caught per item and logged so one conflict cannot abort the batch
- scheduling is gated by `@ConditionalOnProperty("scheduling.enabled")` on `SchedulingConfig` and switched off in the test profile so it cannot race integration tests

Booking path (`ReservationService.book`): load user and bike, reject non-ACTIVE bikes, run a service-level availability pre-check, price the rental, then save. The pre-check is UX only; the database constraint is the guarantee.

Ownership: `confirmReservation` and `cancelReservation` load via `findByIdAndUserId(...)`, so a reservation belonging to another user returns **404** rather than confirming the row exists.

## 8a. Admin bike-status conflict guard (uncommitted)

Taking a bike out of service used to silently strand whatever reservations were on it. The work in progress closes that.

- `ReservationRepository.findActiveConflictsForBike(bikeId, currentDate)` returns PENDING or CONFIRMED reservations on that bike whose `endDate` is still in the future, with the user `JOIN FETCH`ed so the email is available without a lazy load.
- `AdminUserService.updateBikeStatus(bikeId, status, version, force)` runs that query whenever the target status is not ACTIVE.
  - `force=false` (default): throws `BikeUnderActiveRentalException` carrying a `List<ConflictDto>`.
  - `force=true`: cancels each conflict with the reason `"Cancelled by Admin: Bike transitioned to <status>"`, then applies the status change.
- `ConflictDto(UUID reservationId, String userEmail, LocalDate startDate, LocalDate endDate)`.
- `GlobalExceptionHandler` maps `BikeUnderActiveRentalException` to **409** with the conflict list attached as a `conflicts` property on the ProblemDetail, so the admin UI can list exactly what it is about to cancel.
- `PATCH /api/v1/admin/bikes/{id}/status` takes `?force=true|false`, defaulting to false.
- Changelog `012` adds a nullable `cancellation_reason VARCHAR(255)` to `reservations`.

Two defects in this path are recorded in section 13.

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
| 007-update-users.xml | adds `last_modified` and `version`, backfills |
| 008-update-users.xml | renames `joined_date` to `created_at`, converts to timestamptz |
| 009-add-bike-instance-version.xml | `version` column on bike_instances |
| 010-align-reservation-created-at-tz.xml | `reservations.created_at` to timestamptz, matching users |
| 011-add-indexes.xml | `reservations(user_id)`, `reservations(status, created_at)`, `bike_instances(city, status)` |
| 012-add-cancellation-reason-for-reservations.xml | nullable `cancellation_reason` on reservations (uncommitted) |
| dev/999-dev-seed.xml | dev-only seed data (context `dev`) |

There is no `006`; the original seed changelog was renumbered to `dev/999` so seeds always run last.

The dev seed is context-gated, and `application-prod.yml` sets `spring.liquibase.contexts: prod`, so it never runs in production. `src/main/resources/liquibase.yml` configures the **Maven plugin only** (local `diff` / `generateChangeLog` work) and is not read by the running application.

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

61 tests, green on a clean build.

- domain unit tests: `ReservationTest` (parameterised transition matrix), `UserTest`
- pricing unit tests: `RentalCostCalculatorTest` with tier boundaries at 7, 8, 14, 15 and 21 days
- service unit tests with Mockito: `ReservationServiceTest`, `UserServiceTest`, `AuthServiceTest`
- security unit tests: `JwtServiceTest` (forgery, expiry, unique jti), `JwtAuthenticationFilterTest`
- integration tests on Testcontainers (Postgres 16 + Redis 8) via `BaseIntegrationTest`: `ReservationIntegrationTest`, `ReservationConcurrencyIntegrationTest`, `ReservationSchedulerIntegrationTest`, `AuthenticationIntegrationTest`, `UserControllerTest`, `ReservationRepositoryTest`

The concurrency test is the centrepiece: two threads released by a `CountDownLatch` POST the same booking and the suite asserts exactly one 201 and one 409.

That test has **two** valid outcomes, and both must map to 409. Roughly two runs in three the exclusion constraint fires; the remaining third Postgres reports `deadlock detected`, because two concurrent inserts contending on a GiST exclusion constraint for the same key genuinely deadlock. Because of this the test asserts status codes only — asserting an exact body would make it flaky, since the two paths produce different messages. A separate sequential test is the right place to pin the constraint message.

Test seams: `SecurityTestHelper.asUser(...)`, `@WithMockCustomUser`, `TestDataFactory`, and the `Clock` bean, which is replaced with a stubbed instant in the repository, scheduler and reservation integration tests.

No H2 anywhere. Postgres-specific features are tested on Postgres.

Build note: `UserServiceTest` lives in a package spelled `user.Service`. A case-only rename of that package leaves a stale `.class` under the other spelling on a case-insensitive filesystem, and incremental builds then fail with `wrong name: com/velocity/api/user/Service/UserServiceTest`. `mvnw clean test` clears it.

## 13. Known gaps and watchpoints

Accurate as of this update. Do not describe these as working.

**Blocks a usable deployment**
- No Dockerfile, no deployed instance, no Actuator or health endpoint, no structured logging.
- **Production will come up with an empty database.** The seed is `context="dev"` and prod runs `contexts: prod`, so a fresh prod database gets the schema and zero rows: no bikes, no models, no account to log in with. A `prod`-context changelog with fleet data and at least one account is required before the deployed app is usable by anyone.
- `SPRING_PROFILES_ACTIVE` must be pinned in the image; unset, the app falls back to `dev`.

**Defects in the uncommitted bike-status work (section 8a)**
- `AdminUserService.updateBikeStatus` throws `org.springframework.dao.OptimisticLockingFailureException`, but `GlobalExceptionHandler` registers its handler for the subclass `ObjectOptimisticLockingFailureException`. Handler matching is by assignability, and a parent is not assignable to its child, so the throw no longer matches and falls through to the generic handler as a **500**. It was a 409 before the change.
- `findActiveConflictsForBike` filters on `endDate > currentDate`, which includes rentals already under way. Force-cancelling one of those calls `transitionTo(CANCELLED, ...)`, which refuses any cancellation on or after `startDate` with `LateCancelException` (422). So `force=true` fails for exactly the case it exists for — a bike that is out with a customer right now and needs to come off the road.
- The whole path is untested.

**Error contract**
- `CannotAcquireLockException` has no handler, so a deadlock surfaces as a 500.
- An unrecognised `DataIntegrityViolationException` is reported as a 409 conflict even when it is a NOT NULL or FK violation, which is a server bug.
- The `no_overlapping_active_reservations` branch of the constraint-name mapping never matches, because Hibernate does not populate a constraint name for exclusion violations (SQLState 23P01). The overlap 409 is produced by the blanket default instead.

**Resilience**
- `TokenBlacklistRepository.isBlacklisted(...)` runs on every authenticated request with no timeout and no failure handling, so a Redis outage fails every authenticated request with a 500.
- The JWT TTL is 24 hours, long for a token whose revocation depends on Redis being reachable.

**Configuration**
- `ddl-auto: validate` is at a key nothing reads, so there is no schema validation at startup.
- `ClockConfig` returns `Clock.systemDefaultZone()`; every `LocalDate.now(clock)` therefore depends on the host timezone, and a UTC container disagrees with a UTC+2 laptop about the day boundary for the late-cancel rule and the completion scheduler.
- `liquibase.yml` carries a plaintext password.

**Domain**
- The E.164 phone pattern is duplicated in four backend locations (`User.PHONE_PATTERN`, `UserRegistrationRequest`, `UserProfileUpdateRequest`, `AdminUserUpdateRequest`) plus the frontend. They agree today; nothing prevents drift.
- `pricing/exception/InvalidRentalDurationException` is unreferenced.

**Security**
- No rate limiting on `/api/v1/auth/register` or `/api/v1/auth/login`.
- Password policy is length-only; no composition rule, no password-change endpoint.
- Swagger UI is `permitAll` and would be publicly reachable in production.

**Testing**
- `AdminUserService` has no tests at all — self-block, self-demotion, the bike-status version check and the new conflict guard are all unproven.
- Also untested: `AnalyticsService`, `cancelReservation`, and the `findAvailableModels` native query.
- `UserControllerTest` boots a full `@SpringBootTest` plus two containers to exercise one `@PreAuthorize`; it should be a `@WebMvcTest`.
- Seven distinct Spring test context configurations across the suite defeat context caching.
- `UserServiceTest` sits in a package spelled `user.Service`, carries commented-out mocks, and declares a stray `@InjectMocks AuthService`.
- `ReservationConcurrencyIntegrationTest` seeds a user with raw SQL and `phone='123'`, a value the entity's own pattern rejects, alongside a stale comment.
- Dev-seed and test dates are hardcoded in 2026 and have begun to fall into the past.

**CI**
- Runs `mvn clean test` only; no coverage, linting, or frontend workflow.

## 14. Frontend contract notes

The React client lives in a sibling repository and is a presentation layer only.

- the client role literal is `CLIENT`
- `AdminUserResponse` exposes `createdAt`, not `joinedDate`, and does not include `city`
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

## 16. Recent merged work

| PR | Change |
|---|---|
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

The architecture is settled and the error contract is largely complete. The open work is, in order: land the pre-deploy hardening branch, finish and test the bike-status conflict guard, seed a production database, and containerise and deploy. Rate limiting and the admin-surface test gap follow.
