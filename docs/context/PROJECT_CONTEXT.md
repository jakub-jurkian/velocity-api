# VeloCity Fleet API — Project Context

Last updated: 2026-09-01

This file is the single source of truth for the project. It reflects the repo state, the major issue history in the GitHub Kanban board, the architectural decisions captured in ADRs, and the current implementation status for the backend service that powers the VeloCity frontend.

## 1. Project goal

VeloCity is an e-bike rental platform. The backend exists to make the booking flow robust, safe, and testable under real-world concurrency and business rules.

The project is not just CRUD. The core business risk is preventing double-booking for the same physical bike on overlapping dates while keeping the domain model clean and the API easy for a React frontend to consume.

The core product workflow is:
- browse available bikes and models
- register/login as a client
- reserve a bike for a date range
- pay / confirm a reservation
- auto-complete or auto-cancel lifecycle transitions
- manage users and admin actions

## 2. Current implementation status

The project is in the frontend-integration stage: the backend is already feature-rich and issue-driven, and the remaining work is mostly coordination, polish, and integration with the client app.

Implemented areas:
- JWT-backed authentication and authorization
- user registration, login, logout, profile updates, admin user lifecycle
- fleet listing and model availability queries
- reservation creation, ownership checks, lifecycle transitions, and scheduling jobs
- global ProblemDetail-based error handling
- Postgres-backed reservation integrity with exclusion constraints
- comprehensive test coverage including concurrency tests and service tests

Current emphasis:
- frontend contract alignment
- stable DTO contracts and pagination shape
- security + ownership enforcement for client requests
- cleanup of edge cases, observability, and integration hardening

## 3. Tech stack

- Java 25
- Spring Boot 4.1.0
- Spring Web MVC
- Spring Data JPA / Hibernate
- PostgreSQL
- Redis (JWT blacklist)
- Liquibase
- Spring Security + JWT (JJWT)
- Springdoc OpenAPI / Swagger UI
- Maven
- JUnit 5 + Mockito + Spring test support + Testcontainers
- Docker Compose
- GitHub Actions

Important runtime conventions:
- PostgreSQL and Redis are expected on localhost:5432 and localhost:6379 in local dev
- schema changes are Liquibase-managed; JPA ddl-auto is validation-only
- security is stateless with JWT-based filtering
- pricing is BigDecimal-based and configured via spring.pricing.daily-rate

## 4. Architecture and package structure

Core package layout:
- com.velocity.api.auth
- com.velocity.api.user
- com.velocity.api.bike
- com.velocity.api.reservation
- com.velocity.api.security
- com.velocity.api.pricing
- com.velocity.api.common
- com.velocity.api.config

Architectural rules visible in the codebase:
- controllers are thin HTTP adapters
- services orchestrate transactions and business rules
- repositories handle data access
- entities protect their own invariants
- DTOs are used at the API boundary; entities never leak to the web layer
- global error handling returns RFC 7807 ProblemDetail payloads
- method security is enforced for owner and admin use cases

## 5. Domain model

### User
Fields include id, email, passwordHash, fullName, phone, status, role, city, joinedDate.
Rules:
- unique email and phone
- registration via `User.registerClient(...)`
- profile updates via `User.updateProfile(...)`
- block/unblock/softDelete state transitions
- invalid user-state operations throw `InvalidUserStateException`

### BikeModel
Fields include id, name, description, speed, range, capacity, category.
Rules:
- created via `BikeModel.create(...)`
- model-level identity and bike inventory separation

### BikeInstance
Fields include id, status, city, bikeModel.
Status values:
- ACTIVE, MAINTENANCE, LOST, RETIRED
Rules:
- created via `BikeInstance.initialize(...)`
- default ACTIVE state
- booking only allowed when ACTIVE

### Reservation
Fields include id, startDate, endDate, totalCost, status, createdAt, version, user, bikeInstance.
Status values:
- PENDING, CONFIRMED, COMPLETED, CANCELLED
Rules:
- domain creation via `Reservation.book(...)`
- lifecycle enforced through `Reservation.transitionTo(...)`
- invalid transitions throw `InvalidStatusTransitionException`
- `@Version` is used for optimistic locking on update races

## 6. Core business rules and architectural decisions

### ADR-001: concurrency and exclusion constraints
The reservation data integrity requirement is solved by a PostgreSQL exclusion constraint on overlapping active reservations for the same bike.

Key decision:
- the exclusion constraint is the hard guarantee
- service-layer pre-checks are UX convenience only
- `@Version` is not the mechanism for preventing double-booking; it is for update-race safety on existing rows
- date bounds are `[)` to allow back-to-back rentals without phantom conflicts

### ADR-002: rich domain model
The project deliberately rejects anemic entities. Core business logic belongs in entities, not scattered in service classes.

Examples:
- `Reservation.transitionTo(...)`
- `User.updateProfile(...)`
- `User.block()/unblock()/softDelete()`
- domain exceptions and invariant checks remain close to the data they protect

### Core design principles
- strict bounded contexts and package ownership
- no entity leakage to controllers
- centralized pagination and response wrappers
- BigDecimal for all pricing math
- standardized ProblemDetail responses for all error paths
- no public status setters on domain entities
- use factory methods and constructors with restricted visibility

## 7. Security and auth

The application uses JWT-based stateless authentication.

Current security model:
- `SecurityConfig` uses stateless session policy
- `JwtAuthenticationFilter` validates bearer tokens and populates the `SecurityContext`
- `CustomUserDetailsService` resolves authorities from persisted user data
- Redis blacklist stores revoked tokens until expiration
- BCrypt is used for password hashing
- method-level authorization is enabled

Public endpoints:
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- Swagger/OpenAPI docs and API docs endpoints

Protected endpoints include:
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `PATCH /api/v1/users/{id}`
- `GET /api/v1/fleet`
- `POST /api/v1/reservations`
- `GET /api/v1/reservations/availability`
- `GET /api/v1/reservations/my`
- admin user routes

## 8. Reservation logic and lifecycle

The reservation lifecycle is central to the business.

State machine:
- PENDING -> CONFIRMED
- PENDING -> CANCELLED
- CONFIRMED -> COMPLETED
- CONFIRMED -> CANCELLED
- CANCELLED and COMPLETED are terminal

Reservation lifecycle automation:
- stale PENDING reservations are auto-cancelled by a scheduled job
- past-due CONFIRMED reservations are auto-completed by a scheduled job
- scheduling logic catches optimistic locking races gracefully and continues

The project also enforces booking availability and reservation ownership checks at the service layer, while the database constraint prevents race-condition double-booking.

## 9. API surface and frontend-facing contract

Current backend API includes:
- Auth: register, login, logout, profile fetch
- Users: self-update, admin list/block/unblock/delete
- Fleet: searchable/available-bike listing with DTOs and pagination
- Reservations: create, availability query, user's reservations, lifecycle confirmation

Important contract decisions:
- raw entities are not returned to the frontend
- all API responses are DTO-based and paginated where appropriate
- errors are standardized via `ProblemDetail` with `status`, `title`, and `detail`
- `@Valid` request DTOs enforce contract-level validation before service logic

## 10. GitHub issue inventory from the Kanban project

These are the issue themes already created and tracked in the project board. They reflect the actual project sequence from setup to production-hardening and frontend integration.

### Foundation and project setup
- #1 Initial project setup: Spring Boot, Maven, Docker Compose
- #3 Create PostgreSQL schema with Liquibase migrations
- #5 Design and implement core domain entities
- #7 Apply SOLID principles - refactor domain layer & reflect the code
- #10 Add Stream API processing for fleet availability filtering
- #12 Implement bike availability check
- #14 Implement reservation state machine with transition validation (TDD)
- #16 chore: pre-#14 hardening - ADR-001, 404 handler, reservation encapsulation, line endings
- #19 refactor: pre-#14 hardening - ADR-001, 404 handler, reservation encapsulation, line endings
- #21 Architecture review fixes
- #23 Implement RentalCostCalculator with BigDecimal (TDD)
- #25 Add test seam: asUser() helper + SecurityContext stub
- #27 Add GitHub Actions workflow: run mvn test on every push to develop (lightweight CI gate)
- #30 Implement User Registration Endpoint (POST /api/v1/auth/register)
- #33 Enhance internal JavaDocs
- #35 Global error handling: @RestControllerAdvice + ProblemDetail
- #36 Integrate OpenAPI 3 (Swagger)
- #39 Documentation
- #41 First REST controller: GET /v1/fleet, read-only; decide pagination + response envelope here (changing it later breaks the React client)
- #42 Global error handling: @RestControllerAdvice + ProblemDetail
- #45 Add Stream API processing for fleet availability filtering
- #48 Apply SOLID principles - refactor domain layer & reflect the code
- #50 Create PostgreSQL schema with Liquibase migrations
- #52 Design and implement core domain entities
- #54 Initial project setup: Spring Boot, Maven, Docker Compose
- #56 Implement bike availability check
- #58 Implement reservation state machine with transition validation (TDD)
- #60 chore: consistency polish - rate validation, URL prefix, constructor style, doc accuracy
- #62 update README
- #64 Implement reservation creation
- #66 chore: establish AI-assisted workflow - project context file + Gemini Gem
- #68 Integration test for reservation concurrent conflict scenario
- #70 Implement @Scheduled jobs - auto-complete finished rentals; auto-cancel stale PENDING
- #72 Configure JWT authentication filter chain
- #74 Implement login endpoint issuing JWT
- #75 update fetching available bike models

### Issue progression summary
The issue list shows a clear and intentional project evolution:
1. foundation and schema
2. domain + business logic + tests
3. API exposure and error handling
4. reservation concurrency guarantee
5. auth + JWT + user administration
6. lifecycle automation + frontend-facing contract work
7. final integration polish and user workflow expansion

## 11. ADR reference set

Relevant ADRs in the repository:
- `docs/adrs/0001-prevent-reservation-race-conditions.md` — PostgreSQL exclusion constraint for active-reservation overlap prevention
- `docs/adrs/0002-adopt-rich-domain-model-architecture.md` — Rich Domain Model decision
- `docs/adrs/0003-adopt-project-context-file-with-ai-assistant-grounding.md` — AI context management and repo grounding strategy

## 12. Testing strategy

The project expects production-quality testing, not only happy-path checks.

Current test priorities:
- domain rules and state transitions under parameterized tests
- service logic and DTO mapping behavior
- concurrency proof for reservation creation under multiple threads
- integration tests for authentication and ownership enforcement
- validation and error-response mapping with `ProblemDetail`
- repository query tests for availability and overlap logic

## 13. Known risks and current watchpoints

- scheduler cadence is intentionally test/dev-oriented in some phases; production tuning is a deliberate follow-up concern
- security configuration must remain careful to avoid locking Swagger or frontend routes
- database invariants are the source of truth for concurrency safety; the service layer is not the hard guarantee
- frontend integration will surface contract drift if DTOs or pagination envelopes change without coordination
- Java 25 + Spring Boot 4.1.0 requires a consistent local environment; build/test behavior should be aligned across dev, CI, and IDEs

## 14. Working assumptions for ongoing development

- this backend is the source of truth for business rules and database guarantees
- frontend behavior must align with the API contract, not the inverse
- domain invariants are never to be bypassed by setter-based mutation
- exceptions are transformed into structured ProblemDetail payloads rather than leaking raw stack traces or DB internals
- the project remains issue-driven and documentation-backed, with `PROJECT_CONTEXT.md` updated whenever core decisions change

## 15. Summary

The VeloCity API is a backend built around one central integrity problem: ensuring a bike cannot be double-booked for overlapping dates, even under concurrency. Everything from the Postgres exclusion constraint to the rich domain model, JWT auth layer, and reservation lifecycle automation is designed around that guarantee.

The project has moved beyond initial scaffolding into a real application architecture with strong separation of concerns, explicit domain rules, and clear API contracts for the React frontend. The current work is less about inventing a stack and more about integrating and hardening it around the business process it serves.
