# VeloCity Fleet API - AI Context

Last updated: 2026-08-25

> Single source of truth for AI assistants and contributors.
> Keep this file aligned with the actual codebase, application configuration, security setup, and current implementation status.

## 1. Purpose

This project is a Spring Boot backend for an e-bike rental platform. The primary engineering constraints are reservation integrity, exact financial calculations, clear API boundaries, and authenticated user actions. The current codebase is not a greenfield scaffold; it contains a working reservation flow, JWT-based auth, and scheduled lifecycle automation, while still exposing a few temporary implementation shortcuts.

## 2. Current stack

- Java 25
- Spring Boot 4.1.0
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Redis 7 for JWT blacklist storage
- Liquibase
- Spring Security + JWT (JJWT)
- Springdoc OpenAPI / Swagger UI
- Maven
- JUnit 5 + Mockito + Spring test support
- Docker Compose for local infrastructure

Important notes:
- The app expects PostgreSQL and Redis to be running locally on localhost:5432 and localhost:6379.
- ddl-auto is intentionally set to validate; schema changes are Liquibase-managed.
- There is no MapStruct setup. Mapping is currently handwritten in BikeInstanceMapper.

## 3. Current package layout and architecture

Core packages:
- com.velocity.api.user — user model, DTOs, auth endpoints, and profile management
- com.velocity.api.bike — bike domain model, fleet endpoints, and service
- com.velocity.api.reservation — reservation domain, controller, service, and scheduler
- com.velocity.api.security — JWT, filters, custom user details, and blacklist repository
- com.velocity.api.billing — pricing rules
- com.velocity.api.common — shared DTOs, exceptions, and global error handling
- com.velocity.api.config — Spring Security and OpenAPI config

Architecture rules currently visible in code:
- Controllers speak HTTP and DTOs only; business logic stays in services.
- Entities are persisted JPA models and do not expose public setters for most domain mutation.
- Money is handled with BigDecimal.
- Reservation overlap protection is implemented both in application logic and in the database layer.
- ProblemDetail is the standard error envelope via GlobalExceptionHandler.
- Security is stateless; JWTs are validated in a OncePerRequestFilter.

## 4. Domain model (current code)

### User

Fields:
- id, email, passwordHash, fullName, phone, status, role, city, joinedDate

Rules in current code:
- email and phone are unique.
- joinedDate is set in @PrePersist.
- Registration uses User.registerClient(...) and stores a BCrypt hash.
- User.updateProfile(...) validates full name, phone, and city.
- UserStatus is currently ACTIVE, and the role is CLIENT by default.

### BikeModel

Fields:
- id, name, description, speed, range, capacity, category

Rules in current code:
- name is unique.
- This is the abstract model or specification for a bike line.

### BikeInstance

Fields:
- id, status, city, bikeModel

Status values:
- ACTIVE, MAINTENANCE, LOST, RETIRED

Rules in current code:
- A bike is created with BikeInstance.initialize(model, city).
- Current availability logic treats only ACTIVE bikes as bookable.
- The hardware state is checked in reservation creation before database insert.

### Reservation

Fields:
- id, startDate, endDate, totalCost, status, createdAt, version, user, bikeInstance

Status values:
- PENDING, CONFIRMED, COMPLETED, CANCELLED

Rules in current code:
- totalCost is stored as a persisted snapshot.
- createdAt is assigned in @PrePersist.
- @Version is present for optimistic locking on updates.
- Valid transitions are enforced in Reservation.transitionTo(...):
  - PENDING -> CONFIRMED
  - PENDING -> CANCELLED
  - CONFIRMED -> COMPLETED
  - CONFIRMED -> CANCELLED
- Invalid transitions throw InvalidStatusTransitionException.

## 5. Security and authentication

The app currently has a real JWT-based auth layer.

Current security setup:
- SecurityConfig enables stateless sessions and a JWT filter.
- JwtAuthenticationFilter reads the Authorization header and sets an authenticated principal if the token is valid and not blacklisted.
- CustomUserDetailsService loads the user by email.
- CustomUserDetails includes the user UUID as id, which is used in @PreAuthorize checks.
- JwtService creates and validates signed JWTs with JJWT.
- AuthService issues JWTs on login, with extra claims id and role.
- TokenBlacklistRepository stores blacklisted tokens in Redis with a TTL.
- BCryptPasswordEncoder is used for hashing.

Public endpoints currently:
- POST /api/v1/auth/register
- POST /api/v1/auth/login
- Swagger and OpenAPI endpoints under /api/swagger-ui/** and /api/api-docs/**

Authenticated endpoints currently:
- POST /api/v1/auth/logout
- GET /api/v1/auth/me
- PATCH /api/v1/users/{id}
- POST /api/v1/reservations
- GET /api/v1/reservations/availability
- GET /api/v1/fleet

Important implementation note:
- ReservationController.book(...) currently hardcodes the authenticated user UUID to 00000000-0000-0000-0000-000000000001 instead of resolving the real principal from Spring Security.
- This is a temporary in-code shortcut rather than a finished auth integration and should be treated as a known implementation detail while working in the codebase.

## 6. Current API surface

### Auth
- POST /api/v1/auth/register — register a new client account
- POST /api/v1/auth/login — authenticate and return a JWT
- POST /api/v1/auth/logout — blacklist the bearer token in Redis
- GET /api/v1/auth/me — fetch the current authenticated user profile

### Users
- PATCH /api/v1/users/{id} — update a user profile; protected by @PreAuthorize("#id == authentication.principal.id")

### Fleet
- GET /api/v1/fleet — paginated list of active bikes (BikeStatus.ACTIVE)

### Reservations
- POST /api/v1/reservations — book a bike for a date range
- GET /api/v1/reservations/availability — list available model options for a date range

## 7. Reservation logic and booking guarantees

The booking logic is intentionally strong:
- ReservationService.book(...) validates the bike is ACTIVE.
- It checks reservationRepository.isBikeAvailable(...) before save.
- It computes totalCost with RentalCostCalculator based on a BigDecimal daily rate.
- It persists a reservation with status PENDING.
- The database layer adds a PostgreSQL exclusion constraint to prevent active reservation overlaps.

This means the app is trying to satisfy the hard guarantee: the booking cannot double-book a bike at the database level, even if the app-level check happens concurrently.

## 8. Lifecycle automation and scheduled jobs

The app includes a reservation lifecycle scheduler:
- ReservationLifecycleScheduler runs on a fixed interval (@Scheduled(fixedRate = 10000) in current code) for test or dev convenience.
- It cancels stale pending reservations older than 30 minutes.
- It completes past-due confirmed reservations once their end date has passed.

This is active code, but the schedule is not final production timing; it is currently a short-interval job for testing and validation.

## 9. Error handling and validation

GlobalExceptionHandler covers:
- validation failures (MethodArgumentNotValidException -> 400)
- missing resources (ResourceNotFoundException, NoResourceFoundException -> 404)
- duplicate email conflict (EmailAlreadyRegisteredException -> 409)
- database integrity and overlap conflicts (DataIntegrityViolationException, CannotAcquireLockException -> 409)
- invalid reservation transitions (InvalidStatusTransitionException -> 422)
- invalid bike state (InvalidBikeStateException -> 422)
- bad credentials (BadCredentialsException -> 401)
- forbidden actions (AuthorizationDeniedException -> 403)
- generic server errors -> 500

The app uses RFC7807 ProblemDetail responses rather than raw exceptions.

## 10. Database and migration model

Current database conventions in code:
- spring.jpa.hibernate.ddl-auto=validate
- Liquibase is the schema source of truth.
- PostgreSQL-specific overlap handling is part of the schema or changelog rather than a JPA-only concern.
- @Version is present to protect update races but is not the main overlap protection mechanism.

## 11. Current implementation status and known gaps

This app is more advanced than the older planning notes, but it still has obvious gaps:
- JWT auth is implemented and wired into Spring Security.
- Registration, login, logout, and profile retrieval are present.
- Reservation booking and availability checks are implemented.
- Reservation scheduling automation is in place.
- Fleet listing is implemented for ACTIVE bikes and paginated responses.
- Admin fleet management, richer role management, and a full production-grade user or admin lifecycle are not yet implemented.
- The booking action still depends on a hardcoded user ID in ReservationController instead of resolving the authenticated principal.
- The current scheduler cadence is intentionally short for testing and is not a final production schedule.

## 12. Reference files

- Runtime configuration: src/main/resources/application.yaml
- Spring Security config: src/main/java/com/velocity/api/config/SecurityConfig.java
- Auth controller: src/main/java/com/velocity/api/user/controller/AuthController.java
- User profile controller: src/main/java/com/velocity/api/user/controller/UserController.java
- Fleet controller: src/main/java/com/velocity/api/bike/controller/FleetController.java
- Reservation controller: src/main/java/com/velocity/api/reservation/controller/ReservationController.java
- Reservation service: src/main/java/com/velocity/api/reservation/service/ReservationService.java
- Reservation entity: src/main/java/com/velocity/api/reservation/Reservation.java
- User entity: src/main/java/com/velocity/api/user/User.java
- Bike entity: src/main/java/com/velocity/api/bike/BikeInstance.java
- Global exception handler: src/main/java/com/velocity/api/common/exception/GlobalExceptionHandler.java
- Reservation scheduler: src/main/java/com/velocity/api/reservation/scheduler/ReservationLifecycleScheduler.java

## 13. Planning note

This file reflects the actual current state of the repository as of 2026-08-25. If the application evolves, update this document together with the code so AI agents and contributors work from a single accurate source of truth.
