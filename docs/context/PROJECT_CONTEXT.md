# VeloCity Fleet API - AI Context

Last updated: 2026-08-30

> Single source of truth for AI assistants and contributors.
> Keep this file aligned with the actual codebase, application configuration, security setup, and current implementation status.

## 1. Purpose

This project is a Spring Boot backend for an e-bike rental platform. The current implementation emphasizes reservation integrity, authenticated user operations, and explicit lifecycle state transitions for users and reservations. The codebase is beyond initial scaffolding and includes working auth, reservation booking, user self-service, admin user lifecycle operations, and scheduler-driven reservation maintenance.

## 2. Current stack

- Java 25
- Spring Boot 4.1.0
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL
- Redis for JWT blacklist storage
- Liquibase
- Spring Security + JWT (JJWT)
- Springdoc OpenAPI / Swagger UI
- Maven
- JUnit 5 + Spring test support + Testcontainers

Important notes:
- Runtime expects PostgreSQL and Redis on localhost:5432 and localhost:6379 by default.
- `spring.jpa.hibernate.ddl-auto=validate`; schema evolution is Liquibase-managed.
- Security is stateless and enforced through a JWT filter plus method-level authorization.
- Pricing uses configured `pricing.daily-rate` (currently `50.00`) and BigDecimal-based calculation.

## 3. Current package layout and architecture

Core packages:
- `com.velocity.api.auth` — registration, login, logout, and current-user profile retrieval
- `com.velocity.api.user` — user domain model, profile updates, and admin user lifecycle operations
- `com.velocity.api.bike` — bike model/instance domain, availability projections, and fleet listing
- `com.velocity.api.reservation` — reservation domain, booking, listing, and lifecycle scheduler
- `com.velocity.api.security` — JWT service/filter, custom principal details, and Redis token blacklist repository
- `com.velocity.api.pricing` — rental cost calculation
- `com.velocity.api.common` — shared DTOs, enums, exception hierarchy, and global error mapping
- `com.velocity.api.config` — security and infrastructure config

Architecture rules currently visible in code:
- Controllers are thin HTTP adapters and delegate business logic to services.
- Domain entities encapsulate state transitions and avoid broad public mutation.
- Reservation availability is protected at both service and database layers.
- Global error handling standardizes responses via RFC7807 `ProblemDetail`.
- Method security (`@PreAuthorize`) is active and used for user ownership and admin-only routes.

## 4. Domain model (current code)

### User

Fields:
- `id`, `email`, `passwordHash`, `fullName`, `phone`, `status`, `role`, `city`, `joinedDate`

Rules in current code:
- `email` and `phone` are unique.
- `joinedDate` is assigned in `@PrePersist`.
- Registration uses `User.registerClient(...)` with BCrypt-hashed password.
- Profile updates are validated in `User.updateProfile(...)`.
- Domain lifecycle methods exist: `block()`, `unblock()`, `softDelete()`.
- Invalid user state changes throw `InvalidUserStateException`.

### BikeModel

Fields:
- `id`, `name`, `description`, `speed`, `range`, `capacity`, `category`

Rules in current code:
- `name` is unique.
- Created through `BikeModel.create(...)`.

### BikeInstance

Fields:
- `id`, `status`, `city`, `bikeModel`

Status values:
- `ACTIVE`, `MAINTENANCE`, `LOST`, `RETIRED`

Rules in current code:
- Created through `BikeInstance.initialize(...)`.
- Defaults to `ACTIVE`.
- Reservation booking allows only `ACTIVE` instances.

### Reservation

Fields:
- `id`, `startDate`, `endDate`, `totalCost`, `status`, `createdAt`, `version`, `user`, `bikeInstance`

Status values:
- `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`

Rules in current code:
- Created through `Reservation.book(...)`.
- `createdAt` is assigned in `@PrePersist`.
- `@Version` enables optimistic locking.
- Valid transitions in `Reservation.transitionTo(...)`:
  - `PENDING -> CONFIRMED`
  - `PENDING -> CANCELLED`
  - `CONFIRMED -> COMPLETED`
  - `CONFIRMED -> CANCELLED`
- Invalid transitions throw `InvalidStatusTransitionException`.

## 5. Security and authentication

The application has a fully wired JWT-based auth layer.

Current security setup:
- `SecurityConfig` uses stateless session policy and installs `JwtAuthenticationFilter`.
- `JwtAuthenticationFilter` parses bearer token, rejects blacklisted tokens, validates JWT, and populates security context.
- `AuthService` authenticates credentials through `AuthenticationManager`.
- JWT claims include user `id` and `role`.
- Redis-backed blacklist is implemented in `com.velocity.api.security.repository.TokenBlacklistRepository`.
- Password hashing uses `BCryptPasswordEncoder`.
- Method-level security is enabled (`@EnableMethodSecurity`).

Public endpoints currently:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- Swagger/OpenAPI: `/api/swagger-ui.html`, `/api/swagger-ui/**`, `/api/api-docs/**`, `/v3/api-docs/**`

Authenticated endpoints currently include:
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `PATCH /api/v1/users/{id}`
- `GET /api/v1/fleet`
- `POST /api/v1/reservations`
- `GET /api/v1/reservations/availability`
- `GET /api/v1/reservations/my`
- `GET /api/v1/admin/users`
- `POST /api/v1/admin/users/{id}/block`
- `POST /api/v1/admin/users/{id}/unblock`
- `POST /api/v1/admin/users/{id}/delete`

Authorization notes:
- User self-update is protected by `@PreAuthorize("#id == authentication.principal.id")`.
- Admin routes are protected by class-level `@PreAuthorize("hasRole('ADMIN')")`.
- Reservation booking and `/my` reservations resolve user ID from `@AuthenticationPrincipal CustomUserDetails` (no hardcoded ID path).

## 6. Current API surface

### Auth
- `POST /api/v1/auth/register` — register a new client account
- `POST /api/v1/auth/login` — authenticate and return bearer token payload
- `POST /api/v1/auth/logout` — blacklist bearer token until JWT expiration
- `GET /api/v1/auth/me` — fetch current authenticated user profile

### Users
- `PATCH /api/v1/users/{id}` — update own profile (ownership enforced with `@PreAuthorize`)
- `GET /api/v1/admin/users` — list users (admin only, paginated)
- `POST /api/v1/admin/users/{id}/block` — block user (admin only)
- `POST /api/v1/admin/users/{id}/unblock` — unblock user (admin only)
- `POST /api/v1/admin/users/{id}/delete` — soft-delete user (admin only)

### Fleet
- `GET /api/v1/fleet` — paginated list of active bikes

### Reservations
- `POST /api/v1/reservations` — create reservation for authenticated user
- `GET /api/v1/reservations/availability` — get available models for date range
- `GET /api/v1/reservations/my` — get authenticated user reservations (paginated)

## 7. Reservation logic and booking guarantees

Current booking workflow in `ReservationService.book(...)`:
- Loads authenticated user and target bike instance.
- Rejects non-`ACTIVE` bikes (`InvalidBikeStateException`).
- Checks overlap with `reservationRepository.isBikeAvailable(...)`.
- Calculates cost with `RentalCostCalculator` based on day count and configured daily rate.
- Persists reservation with initial `PENDING` status.

Data consistency model:
- Application-layer overlap check provides fast domain-level rejection.
- Database-level protections (including overlap constraint from ADR direction) prevent race-condition double-booking under concurrency.
- Reservation listing for current user uses repository paging with entity graph loading of bike and model relations.

## 8. Lifecycle automation and scheduled jobs

`ReservationLifecycleScheduler` is active and currently test/dev-tuned:
- `@Scheduled(fixedRate = 10000)` cancel job: finds stale `PENDING` reservations older than 30 minutes and transitions to `CANCELLED`.
- `@Scheduled(fixedRate = 10000)` complete job: finds past-due `CONFIRMED` reservations and transitions to `COMPLETED`.
- Scheduler catches optimistic locking races to avoid crashing the job loop.

Note:
- The 10-second cadence is explicitly temporary/test-oriented (production-style schedules are present as commented intent).

## 9. Error handling and validation

`GlobalExceptionHandler` maps known failures to `ProblemDetail`:
- validation errors (`MethodArgumentNotValidException`) -> 400 with `invalidFields`
- missing resources (`ResourceNotFoundException`, `NoResourceFoundException`) -> 404
- duplicate registration email (`EmailAlreadyRegisteredException`) -> 409
- bike availability/business conflicts (`BikeNotAvailableException`) -> 409
- data integrity/locking conflicts (`DataIntegrityViolationException`, `CannotAcquireLockException`) -> 409
- invalid state transitions (`InvalidStatusTransitionException`, `InvalidUserStateException`) -> 422
- invalid bike state (`InvalidBikeStateException`) -> 422
- invalid credentials (`BadCredentialsException`) -> 401
- forbidden actions (`AuthorizationDeniedException`) -> 403
- unsupported HTTP method (`HttpRequestMethodNotSupportedException`) -> 405
- fallback unexpected errors -> 500

Validation strategy:
- Request DTO validation (`@Valid`) at controller boundaries.
- Additional domain-level validation inside entity methods and services.

## 10. Database and migration model

Current database conventions:
- PostgreSQL is the runtime relational database.
- Liquibase is the schema source of truth.
- Hibernate runs in validation mode (`ddl-auto=validate`) and does not auto-mutate schema.
- Reservation entity optimistic locking (`@Version`) is active.
- Reservation overlap protection is designed as both application check and database-enforced constraint.

## 11. Current implementation status and known gaps

Implemented:
- JWT auth flow (register/login/logout/me) with Redis blacklist.
- User self-profile updates.
- Admin user lifecycle endpoints (list/block/unblock/soft-delete).
- Fleet active-bike listing with pagination.
- Reservation booking, availability query, and authenticated user reservation list (`/my`).
- Scheduled stale-cancel and past-due-complete reservation transitions.

Known gaps and risks:
- Scheduler frequencies are test/dev-oriented and not yet production-tuned.
- Security/CORS origins are currently local frontend focused (`localhost:5173`, `localhost:3000`).
- Build tooling currently targets very new stack coordinates (Java 25 + Spring Boot 4.1.0), which may require environment alignment in IDE/CI.

## 12. Reference files

- Runtime configuration: `src/main/resources/application.yaml`
- Build and dependencies: `pom.xml`
- Security config: `src/main/java/com/velocity/api/config/SecurityConfig.java`
- Auth controller: `src/main/java/com/velocity/api/auth/controller/AuthController.java`
- Auth service: `src/main/java/com/velocity/api/auth/service/AuthService.java`
- User controller: `src/main/java/com/velocity/api/user/controller/UserController.java`
- Admin controller: `src/main/java/com/velocity/api/user/controller/AdminUserController.java`
- Fleet controller: `src/main/java/com/velocity/api/bike/controller/FleetController.java`
- Reservation controller: `src/main/java/com/velocity/api/reservation/controller/ReservationController.java`
- Reservation service: `src/main/java/com/velocity/api/reservation/service/ReservationService.java`
- Reservation repository: `src/main/java/com/velocity/api/reservation/repository/ReservationRepository.java`
- Reservation scheduler: `src/main/java/com/velocity/api/reservation/scheduler/ReservationLifecycleScheduler.java`
- Reservation entity: `src/main/java/com/velocity/api/reservation/Reservation.java`
- User entity: `src/main/java/com/velocity/api/user/User.java`
- Bike instance entity: `src/main/java/com/velocity/api/bike/BikeInstance.java`
- Global exception handler: `src/main/java/com/velocity/api/common/exception/GlobalExceptionHandler.java`
- JWT filter: `src/main/java/com/velocity/api/security/JwtAuthenticationFilter.java`
- Token blacklist repository: `src/main/java/com/velocity/api/security/repository/TokenBlacklistRepository.java`

## 13. Planning note

This file reflects the repository state as of 2026-08-30. Update it together with meaningful architectural, API, security, or workflow changes so assistants and contributors remain grounded in current reality.
