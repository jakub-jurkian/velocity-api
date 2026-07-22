# ADR-002: Adopt Rich Domain Model Architecture

## Status
Accepted

## Context
As we build complex business workflows (such as the ebike Reservation state machine), we must decide where the core business rules live. Historically, many Spring Boot applications default to an "Anemic Domain Model," where entities are merely data containers (simple getters and setters), and all validation and state transition logic is scattered across various Service classes.

We evaluated the trade-offs between continuing with an Anemic Domain Model versus adopting a Rich Domain Model, specifically for ensuring that entities cannot be forced into invalid or illegal states (e.g., transitioning a `CANCELLED` reservation directly to `COMPLETED`).

## Decision
We will adopt a **Rich Domain Model** architecture.

Core business rules, state transitions, and internal validations will be encapsulated directly inside the JPA Entities.
- Entities will expose behavior-driven methods (e.g., `transitionTo(ReservationStatus newStatus)`) rather than public setters for critical fields.
- Entities are responsible for protecting their own invariants and throwing specific domain exceptions (e.g., `InvalidStatusTransitionException`) if an illegal operation is attempted.
- Service classes will act strictly as orchestrators. A service will retrieve the entity, delegate the operation to the entity's behavioral method, and persist the result using Spring's `@Transactional` boundaries. Services will contain zero `if/else` logic regarding entity state validation.

## Consequences

### Positive
- **Guaranteed Consistency:** It becomes programmatically impossible to save an entity in an invalid state, regardless of which controller, service, or background job modifies it.
- **Improved Testability:** Core business rules can be exhaustively unit-tested using pure Java (e.g., `@ParameterizedTest` matrices) without needing Mockito or Spring Contexts.
- **Thin Services:** Service classes become highly readable, focusing purely on orchestration (database fetching, transaction management, and external API calls).
- **High Cohesion:** The data and the rules governing that data live in the exact same file.

### Negative
- **Learning Curve:** Developers accustomed to Anemic Domain Models must break the habit of putting business logic in Service classes and heavily utilizing Lombok `@Setter` on entities.
- **Entity Size:** Entity classes will naturally grow larger in line count as they absorb the logic that previously lived in the service layer.