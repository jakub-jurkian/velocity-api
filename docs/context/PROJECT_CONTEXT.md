# VeloCity Fleet API — Project Context

Last updated: 2026-09-20

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

The project is in the deployment-preparation stage. The backend is feature-complete for the demo flow and the error contract is settled; remaining work is containerisation, observability, and test coverage of the admin surface.

Implemented:
- JWT authentication with per-request account-status enforcement
- single-parse JWT verification; tokens carry a `jti` and the Redis blacklist keys on it
- user registration, login, logout, profile updates, admin user lifecycle
- fleet counts and date-range availability with server-side pricing
- reservation creation, ownership checks, lifecycle transitions, scheduled jobs
- admin analytics aggregates
- ProblemDetail-based error handling, including 401/403 semantics and framework-level 4xx mapping
- Postgres-backed reservation integrity via an exclusion constraint
- concurrency, integration, repository and domain tests on Testcontainers

Not yet implemented (see section 13):
- Dockerfile, deployment, Actuator, structured logging
- rate limiting on the auth endpoints
- test coverage for `AdminUserService`, `AnalyticsService`, `cancelReservation`, and the availability query

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
- schema changes are Liquibase-managed
- `open-in-view: false`; no lazy loading outside a transaction
- security is stateless with JWT-based filtering
- Redis repository scanning is disabled (`spring.data.redis.repositories.enabled: false`)
- pricing is BigDecimal-based, bound by `PricingProperties` from the top-level `pricing.*` prefix (NOT `spring.pricing.*`)
- configuration properties classes are discovered via `@ConfigurationPropertiesScan` on the main class

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
Fields: `id`, `startDate`, `endDate`, `totalCost` (BigDecimal), `status`, `createdAt` (Instant, `@CreatedDate`), `user` (LAZY), `bikeInstance` (LAZY), `version` (`@Version`).

`ReservationStatus`: PENDING, CONFIRMED, COMPLETED, CANCELLED.

Rules:
- created via `Reservation.book(...)`; duration must be 3-21 days; start date must be strictly in the future
- null arguments are rejected before any value-based validation runs
- lifecycle enforced by `Reservation.transitionTo(newStatus, currentDate)`
- same-status transition is a deliberate no-op
- cancelling on or after `startDate` throws `LateCancelException` (422)
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
| PATCH | `/api/v1/admin/bikes/{id}/status` | ADMIN, optimistic-locked via a `version` in the body |
| GET | `/api/v1/admin/analytics` | ADMIN |

Swagger UI is served at `/api/swagger-ui.html` and the OpenAPI document at `/api/api-docs`.

There is no delete-user endpoint. Admin user management is list, block, unblock, update, change role.

Contract decisions:
- entities are never returned; every response is a DTO record
- list endpoints are wrapped in `PaginatedResponse<T>` with a `data` array and a `meta` object (`currentPage`, `pageSize`, `totalElements`, `totalPages`, `isFirst`, `isLast`, `hasNext`, `hasPrevious`). Clients must read `meta`, not just `data`.
- errors are `application/problem+json` with `status`, `title`, `detail`, `instance`; validation failures add an `invalidFields` map
- PATCH bodies use `JsonNullable<T>` for tri-state semantics: field absent means "leave unchanged", explicit `null` is a validation error
- duplicate email and phone are pre-checked in the service layer and return **409** with a fixed message that does not echo the submitted value; if a race slips past the pre-check, `GlobalExceptionHandler` reads the Postgres constraint name and produces the same response
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
| dev/999-dev-seed.xml | dev-only seed data (context `dev`) |

There is no `006`; the original seed changelog was renumbered to `dev/999` so seeds always run last.

The dev seed is context-gated, and `application-prod.yml` sets `spring.liquibase.contexts: prod`, so it never runs in production. `src/main/resources/liquibase.yml` configures the **Maven plugin only** (local `diff` / `generate-changelog` work) and is not read by the running application.

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

Suite composition (`src/test/java`), 61 tests:
- domain unit tests: `ReservationTest` (parameterised transition matrix), `UserTest`
- pricing unit tests: `RentalCostCalculatorTest` with tier boundaries at 7, 8, 14, 15 and 21 days
- service unit tests with Mockito: `ReservationServiceTest`, `UserServiceTest`, `AuthServiceTest`
- security unit tests: `JwtServiceTest` (forgery, expiry, unique jti), `JwtAuthenticationFilterTest`
- integration tests on Testcontainers (Postgres 16 + Redis 8) via `BaseIntegrationTest`: `ReservationIntegrationTest`, `ReservationConcurrencyIntegrationTest`, `ReservationSchedulerIntegrationTest`, `AuthenticationIntegrationTest`, `UserControllerTest`, `ReservationRepositoryTest`

The concurrency test is the centrepiece: two threads released by a `CountDownLatch` POST the same booking and the suite asserts exactly one 201 and one 409.

Test seams: `SecurityTestHelper.asUser(...)`, `@WithMockCustomUser`, `TestDataFactory`, and the `Clock` bean, which is replaced with a stubbed instant in the repository, scheduler and reservation integration tests.

No H2 anywhere. Postgres-specific features are tested on Postgres.

## 13. Known gaps and watchpoints

Accurate as of this update. Do not describe these as working.

**Configuration**
- `ddl-auto: validate` sits under `spring.jpa.properties.hibernate.ddl-auto`, which is neither Boot's key (`spring.jpa.hibernate.ddl-auto`) nor Hibernate's (`hibernate.hbm2ddl.auto`). It is silently ignored, so there is currently **no schema validation at startup**.
- `ClockConfig` returns `Clock.systemDefaultZone()`, so every `LocalDate.now(clock)` depends on the host timezone. A UTC container and a UTC+2 laptop disagree about the day boundary for the late-cancel rule and the completion scheduler. A zone must be chosen deliberately.
- `src/main/resources/liquibase.yml` is committed with a plaintext password and no `contexts`; if it were ever pointed at a non-local database, Liquibase with no context specified runs **all** changesets, including the dev seed.
- If `SPRING_PROFILES_ACTIVE` is unset, the app falls back to the `dev` profile and fails at startup against `localhost:5432` with a misleading connection error. The deployment image should pin the profile.

**Resilience**
- `TokenBlacklistRepository.isBlacklisted(...)` is called on every authenticated request with no timeout and no fallback. A Redis outage therefore fails every authenticated request with a 500. A fail-open-and-log policy plus a bounded command timeout is the intended fix.
- The JWT TTL is 24 hours, which is long for a token whose revocation depends on Redis being reachable.

**Error contract**
- `GlobalExceptionHandler` maps every unrecognised `DataIntegrityViolationException` to **409**. A NOT NULL or foreign-key violation is a server bug and should be a logged 500.
- `CannotAcquireLockException` (deadlock, lock timeout) shares that handler although it is a transient, retryable condition rather than an integrity violation.

**Domain**
- `AdminUserService.updateBikeStatus(...)` moves a bike to MAINTENANCE, LOST or RETIRED without checking for PENDING or CONFIRMED reservations on it. The bike disappears from availability while the customer keeps a booking for an out-of-service bike, and nothing notifies anyone.
- The E.164 phone pattern is duplicated in four backend locations (`User.PHONE_PATTERN`, `UserRegistrationRequest`, `UserProfileUpdateRequest`, `AdminUserUpdateRequest`) plus the frontend. They agree today; nothing prevents drift.
- `pricing/exception/InvalidRentalDurationException` is unreferenced; the calculator throws `DomainValidationException`.

**Testing**
- `AdminUserService` has **no tests at all** — self-block, self-demotion and the bike-status version check are entirely unproven.
- Also untested: `AnalyticsService`, `cancelReservation`, and the `findAvailableModels` native query.
- The concurrency test asserts status codes only; it never asserts the body of the 409.
- `UserControllerTest` boots a full `@SpringBootTest` plus two containers to exercise one `@PreAuthorize`; it should be a `@WebMvcTest`.
- Seven distinct Spring test context configurations across the suite defeat context caching.
- `UserServiceTest` lives in a package spelled `user.Service`, carries commented-out mocks, and declares a stray `@InjectMocks AuthService`.
- `ReservationConcurrencyIntegrationTest` seeds a user with raw SQL and `phone='123'`, a value the entity's own pattern rejects, alongside a stale comment.
- Dev-seed and test dates are hardcoded in 2026 and have begun to fall into the past.

**Missing infrastructure**
- no Dockerfile and no deployed instance
- no Spring Boot Actuator or health endpoint
- no structured logging or MDC request correlation
- no rate limiting on `/api/v1/auth/register` or `/api/v1/auth/login`
- Swagger UI is `permitAll` and would be publicly reachable in production
- CI runs `mvn clean test` only; no coverage, linting, or frontend workflow

## 14. Frontend contract notes

The React client lives in a sibling repository and is a presentation layer only.

- the client role literal is `CLIENT`
- `AdminUserResponse` exposes `createdAt`, not `joinedDate`, and does not include `city`
- money comes from the server quote; the client must not recompute totals
- `endDate` is exclusive in every date calculation
- list responses are enveloped; read `meta` for pagination
- CORS origins come from `cors.allowed-origins` per profile
- the phone pattern `^\+[1-9]\d{7,14}$` is mirrored in `src/utils/validators.ts` and must be changed in both repositories together
- a duplicate email or phone returns 409 with a distinct `title` and `detail` per field, so the client can surface a field-specific message

## 15. Conventions

- issue-driven development; branch per issue, PR closes the issue
- issue and PR templates live in `.github/`
- commit style: `type(#issue): summary`, e.g. `fix: adjust calculator & pricing logic according to github issue (#101)`
- ADRs are added for decisions that would otherwise be re-litigated
- this file is updated when core decisions or contracts change

## 16. Recent merged work

| Commit | Change |
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
| #85 | analytics endpoint |

## 17. Summary

VeloCity is a backend built around one integrity problem: a bike must never be double-booked for overlapping dates, even under concurrency. The exclusion constraint, the rich domain model, the JWT layer and the lifecycle automation all serve that guarantee.

The architecture is settled and the error contract is complete. The open work is production readiness: containerisation and deployment, a health endpoint and structured logging, Redis failure tolerance, rate limiting on the auth endpoints, and closing the test gap around the admin surface.
