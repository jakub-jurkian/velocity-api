# VeloCity Fleet API - AI Context

Last updated: 2026-07-27

> Single source of truth for AI assistants and contributors.
> Keep this file aligned with the codebase, schema, ADRs, and delivery plan.

## 1. Purpose

Read this before changing code, generating issues, or opening PRs. This project is a Spring Boot backend for an e-bike rental platform. The important constraints are not "generic CRUD" constraints; they are reservation integrity, money accuracy, and clear API boundaries.

## 2. Stack

- Java 25
- Spring Boot 4.1.0
- Spring Data JPA / Hibernate
- PostgreSQL 16
- Liquibase
- Maven
- JUnit 5
- Mockito
- Spring Security crypto + spring-security-test
- springdoc-openapi

Notes:
- DTOs are used at controller boundaries, but MapStruct is not wired in yet.
- No JWT implementation exists yet.
- No Testcontainers usage exists yet.

## 3. Domain Model

### User

- Fields: `id`, `email`, `passwordHash`, `fullName`, `phone`, `status`, `role`, `city`, `joinedDate`
- Relationships: `User -> Reservation` one-to-many
- Rules:
  - `email` and `phone` are unique.
  - `joinedDate` is set in `@PrePersist`.
  - Registration always stores a BCrypt hash, never a plain password.

### BikeModel

- Fields: `id`, `name`, `description`, `speed`, `range`, `capacity`, `category`
- Relationships: `BikeModel -> BikeInstance` one-to-many
- Rules:
  - `name` is unique.
  - This is the abstract bike type, not a physical bike.

### BikeInstance

- Fields: `id`, `status`, `city`, `bikeModel`
- Status enum: `ACTIVE`, `MAINTENANCE`, `LOST`, `RETIRED`
- Relationships:
  - `BikeInstance -> BikeModel` many-to-one
  - `BikeInstance -> Reservation` one-to-many
- Rules:
  - Availability queries currently treat `ACTIVE` bikes as bookable.
  - There is no bike status transition service yet.

### Reservation

- Fields: `id`, `startDate`, `endDate`, `totalCost`, `status`, `createdAt`, `version`, `user`, `bikeInstance`
- Status enum: `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`
- Relationships:
  - `Reservation -> User` many-to-one
  - `Reservation -> BikeInstance` many-to-one
- Rules:
  - `totalCost` is a persisted snapshot.
  - `createdAt` is set in `@PrePersist`.
  - `@Version` exists for lost-update protection on updates to an existing row.
  - Valid transitions:
    - `PENDING -> CONFIRMED`
    - `PENDING -> CANCELLED`
    - `CONFIRMED -> COMPLETED`
    - `CONFIRMED -> CANCELLED`
  - Invalid transitions throw `InvalidStatusTransitionException`.

## 4. Architectural Rules

- **DTOs at every controller boundary.** Entities do not leave the service layer, which keeps persistence details and lazy-loading concerns out of the API contract.
- **Money uses `BigDecimal` only.** This avoids floating-point rounding errors in rental pricing.
- **Reservation overlap is enforced in PostgreSQL.** The hard guarantee is a Liquibase-managed exclusion constraint on active reservations using `daterange(..., '[)')` and `btree_gist`; service-side checks are only for friendlier 409 responses.
- **`@Version` is for update races, not booking overlap.** It protects state changes on existing reservation rows, not the initial insert race.
- **Status transitions stay out of controllers.** Reservation lifecycle rules live in the domain entity and service layer so they stay testable and centralized.
- **List endpoints return paged responses.** The current pattern is `Page<T>` in the service and `PaginatedResponse<T>` at the API boundary.
- **Validation lives on request DTOs.** `@Valid`, bean validation annotations, and `ProblemDetail` responses are the standard.
- **Liquibase is the schema source of truth.** `spring.jpa.hibernate.ddl-auto=validate` is intentional.
- **`open-in-view` stays off.** Controllers must not rely on lazy entity access after transaction boundaries.
- **Passwords are always hashed with BCrypt.** Plain-text passwords must never be persisted or returned.

## 5. Anti-Patterns

- No field injection
- No entities returned from controllers
- No business logic in controllers
- No trusting client-sent prices
- No double-booking by application-side check-then-act logic
- No `double` or `float` for money
- No changing reservation state from the controller layer

## 6. Glossary

- **BikeModel** - the abstract bike type, such as a product line or spec sheet
- **BikeInstance** - one physical bike with its own ID, city, status, and model reference
- **Reservation** - a booking for one bike instance over a date range
- **Active reservation** - a reservation with status `PENDING` or `CONFIRMED`

## 7. Conventions

- Issue template: `.github/issue_template.md`
- PR template: `.github/pull_request_template.md`
- Commit style: conventional commits are used in history (`docs: ...`, `feat: ...`, etc.)
- Branch naming: concise slash-separated names are used in practice, for example `docs/update-readme`
- Testing: JUnit 5 + Mockito, with parameterized domain tests and thin integration tests
- CI: GitHub Actions runs `mvn clean test` on `main` against PostgreSQL 16 and JDK 25
- API docs: Swagger UI at `/api/swagger-ui.html`, OpenAPI JSON at `/api/api-docs`

## 8. Current Sprint / Where I Am

- Day 27/90
- Last completed: core domain entities, rental calculator, auth registration, fleet listing, RFC 7807 error handling, Liquibase schema, and seed data
- In progress: the working API/security phase from the 90-day plan
- Next up: reservation creation flow, concurrency-safe booking enforcement, and JWT-based security

## 9. Reference, Don't Duplicate

- Full schema: `src/main/resources/db/changelog/`
- Runtime config: `src/main/resources/application.yaml`
- Liquibase config: `src/main/resources/liquibase.yml`
- ADRs:
  - `docs/adrs/0001-prevent-reservation-race-conditions.md`
  - `docs/adrs/0002-adopt-rich-domain-model-architecture.md`
- API docs: Springdoc OpenAPI

## 10. Current API Surface

- `POST /api/v1/auth/register`
- `GET /api/v1/fleet`

## 11. Current Implementation Notes

- `UserService` handles registration and uses `PasswordEncoder`.
- `FleetService` returns only active bike instances.
- `ReservationService` currently supports status transitions for an existing reservation by ID.
- `GlobalExceptionHandler` maps validation failures, missing resources, conflicts, invalid transitions, and uncaught exceptions to `ProblemDetail`.
- There is no reservation create endpoint yet.
- There is no JWT filter, token service, or login endpoint yet.
- The current mapper is handwritten (`BikeInstanceMapper`), not MapStruct-generated.

## 12. Plan Alignment

The 90-day plan in `VeloCity_Fleet_API_Plan_90_Days.docx` is a guide, not a perfect match for current code. Current code already includes a few additions and corrections:

- Rich domain behavior for reservations is implemented.
- Pricing is server-side and BigDecimal-based.
- Reservation overlap strategy follows ADR-001 and uses a database constraint, not optimistic locking alone.
- Security is only partially present so far.
