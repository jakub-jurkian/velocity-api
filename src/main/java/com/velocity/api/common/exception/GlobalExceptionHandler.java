package com.velocity.api.common.exception;

import com.velocity.api.bike.exception.BikeNotAvailableException;
import com.velocity.api.bike.exception.BikeUnderActiveRentalException;
import com.velocity.api.bike.exception.InvalidBikeStateException;
import com.velocity.api.bike.exception.InvalidBikeStatusTransitionException;
import com.velocity.api.reservation.exception.InvalidStatusTransitionException;
import com.velocity.api.reservation.exception.LateCancelException;
import com.velocity.api.security.exception.InvalidTokenException;
import com.velocity.api.user.exception.CannotDemoteSelfException;
import com.velocity.api.user.exception.EmailAlreadyRegisteredException;
import com.velocity.api.user.exception.InvalidUserStateException;
import com.velocity.api.user.exception.PhoneAlreadyRegisteredException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Global exception handler that intercepts exceptions thrown by the application
 * and maps them to standardized RFC 7807 ProblemDetail JSON responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Catches validation errors when a request body fails @Valid constraints.
     * Maps to HTTP 400 Bad Request.
     *
     * @param ex the exception containing the validation errors
     * @return a ProblemDetail object with validation failure details
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                "Validation failed for one or more fields."
        );
        problem.setTitle("Bad Request");
        problem.setType(URI.create("about:blank"));

        Map<String, List<String>> errors = new HashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.computeIfAbsent(fieldError.getField(), _ -> new ArrayList<>())
                    .add(fieldError.getDefaultMessage());
        }

        problem.setProperty("invalidFields", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Catches errors when a requested domain entity is not found in the database.
     * Maps to HTTP 404 Not Found.
     *
     * @param ex the custom exception containing the resource missing message
     * @return a ProblemDetail object with the 404 status
     */

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("about:blank"));

        return problem;
    }

    /**
     * Catches domain-specific registration conflicts.
     * Maps to HTTP 409 Conflict.
     *
     * @param ex the email already registered exception
     * @return a ProblemDetail object explaining the duplicate email
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegisteredException(EmailAlreadyRegisteredException ex) {
        log.warn("Resource Conflict: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Resource Conflict");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    @ExceptionHandler(PhoneAlreadyRegisteredException.class)
    public ProblemDetail handlePhoneAlreadyRegisteredException(PhoneAlreadyRegisteredException ex) {
        log.warn("Resource Conflict: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Resource Conflict");
        problem.setType(URI.create("about:blank"));
        return problem;
    }


    private record ErrorMapping(HttpStatus status, String title, String detail) {
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String constraint = extractConstraintName(ex);

        ErrorMapping mapped = switch (constraint == null ? "" : constraint.toLowerCase()) {
            case "no_overlapping_active_reservations" -> new ErrorMapping(HttpStatus.CONFLICT,
                    "Bike Not Available", "This bike is already reserved for the selected dates.");
            case "uc_usersemail_col" -> new ErrorMapping(HttpStatus.CONFLICT,
                    "Duplicate Record", "An account with this email already exists.");
            case "uc_usersphone_col" -> new ErrorMapping(HttpStatus.CONFLICT,
                    "Duplicate Record", "An account with this phone number already exists.");
            default -> null;
        };

        if (mapped == null) {
            log.error("Unmapped data integrity violation (constraint={})", constraint, ex);
            mapped = new ErrorMapping(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Internal Server Error", "An unexpected internal server error occurred.");
        } else {
            log.warn("Database constraint violated: {}", constraint);
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(mapped.status(), mapped.detail());
        problem.setTitle(mapped.title());
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    private String extractConstraintName(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve
                    && cve.getConstraintName() != null) {
                return cve.getConstraintName();
            }
            // Hibernate does not extract names for exclusion violations (SQLState 23P01)
            if (cause instanceof java.sql.SQLException sqle
                    && "23P01".equals(sqle.getSQLState())) {
                return "no_overlapping_active_reservations";
            }
            cause = cause.getCause();
        }
        return null;
    }


    /**
     * Catches illegal reservation state transition attempts.
     * Maps to HTTP 422 Unprocessable Entity.
     *
     * @param ex the exception containing the transition violation details
     * @return a ProblemDetail object with the 422 status
     */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ProblemDetail handleInvalidStatusTransitionException(InvalidStatusTransitionException ex) {
        log.warn("Invalid State Change: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
        problem.setTitle("Invalid State Transition");
        return problem;
    }

    @ExceptionHandler(CannotAcquireLockException.class)
    public ProblemDetail handleCannotAcquireLockException(CannotAcquireLockException ex) {
        log.warn("Lock acquisition failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource is busy. Please try again.");
        problem.setTitle("Modification conflict");
        return problem;
    }

    /**
     * Catches conflicts when a requested bike is already booked for the target dates.
     * Maps to HTTP 409 Conflict.
     *
     * @param ex the exception indicating schedule overlap
     * @return a ProblemDetail object with the 409 status
     */
    @ExceptionHandler(BikeNotAvailableException.class)
    public ProblemDetail handleBikeNotAvailableException(BikeNotAvailableException ex) {
        log.warn("Bike is not available: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Bike not available");
        return problem;
    }

    /**
     * Catches errors when a physical bike's hardware status (e.g., MAINTENANCE, RETIRED)
     * prevents it from being booked.
     * Maps to HTTP 422 Unprocessable Entity.
     *
     * @param ex the exception containing the invalid bike hardware state message
     * @return a ProblemDetail object with the 422 status
     */
    @ExceptionHandler(InvalidBikeStateException.class)
    public ProblemDetail handleInvalidBikeStateException(InvalidBikeStateException ex) {
        log.warn("Invalid bike state for reservation: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
        problem.setTitle("Invalid Bike State");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    @ExceptionHandler(DomainValidationException.class)
    public ProblemDetail handleDomainValidationException(DomainValidationException ex) {
        log.warn("Invalid argument: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
        problem.setTitle("Invalid argument");
        return problem;
    }

    /**
     * Only the login path reaches this: the AuthenticationManager throws it when
     * the submitted email and password do not match. Token failures have their
     * own type, so this message can be specific without misleading anyone.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Incorrect email or password."
        );
        problem.setTitle("Unauthorized");
        return problem;
    }

    /**
     * A bearer token that is malformed, expired, revoked or missing its subject.
     * The remedy is a fresh login, not a password reset — which is what the
     * client was being told while this shared BadCredentialsException.
     *
     * <p>The reason is logged but never returned: which of the four ways a token
     * failed is not the caller's business.
     */
    @ExceptionHandler(InvalidTokenException.class)
    public ProblemDetail handleInvalidTokenException(InvalidTokenException ex) {
        log.warn("Rejected token: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Your session is no longer valid. Please log in again."
        );
        problem.setTitle("Session Expired");
        return problem;
    }

    @ExceptionHandler(InvalidUserStateException.class)
    public ProblemDetail handleInvalidUserStateException(InvalidUserStateException ex) {
        log.warn("Invalid User State Change: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
        problem.setTitle("Invalid User State Change");
        return problem;
    }

    @ExceptionHandler(CannotDemoteSelfException.class)
    public ProblemDetail handleCannotDemoteSelfException(CannotDemoteSelfException ex) {
        log.warn("Self-demotion attempt blocked: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Self-demotion attempt blocked");
        return problem;
    }

    @ExceptionHandler(LateCancelException.class)
    public ProblemDetail handleLateCancellationException(LateCancelException ex) {
        log.warn("Late cancellation attempt blocked: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
        problem.setTitle("Late Cancellation Policy Violation");
        return problem;
    }

    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLockedException(LockedException ex) {
        log.warn("The account is blocked: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Your account has been blocked. Please contact support."
        );
        problem.setTitle("Account has been blocked");
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationException(AuthenticationException ex) {
        log.debug("Unauthorized: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "A valid authentication token is required. Please log in."
        );
        problem.setTitle("Unauthorized");
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "You do not have the required permissions to access this resource."
        );
        problem.setTitle("Forbidden");
        return problem;
    }

    // Registered on the parent, not on ObjectOptimisticLockingFailureException.
    // Handler matching is by assignability, so a handler declared for the
    // subclass does not catch a parent thrown by hand — which is what
    // AdminUserService.updateBikeStatus does to attach its own message.
    // Registering here still covers the ORM subclass the scheduler sees.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleAObjectOptimisticLockingFailureException(OptimisticLockingFailureException ex) {
        log.warn("Optimistic Locking Failure: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "The resource was modified by another process. Please refresh the latest state and try again."
        );
        problem.setTitle("Modification conflict");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            @NonNull Exception ex, @Nullable Object body,
            @NonNull HttpHeaders headers, @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (problem.getTitle() == null) {
                problem.setTitle(HttpStatus.valueOf(status.value()).getReasonPhrase());
            }
            problem.setType(URI.create("about:blank"));
            if (problem.getInstance() == null && request instanceof ServletWebRequest swr) {
                problem.setInstance(URI.create(swr.getRequest().getRequestURI()));
            }
        }
        if (status.is4xxClientError()) {
            log.warn("Framework client error encountered [{}] : {}", status.value(), ex.getMessage());
        }
        if (status.is5xxServerError()) {
            log.error("Server error encountered [{}] : {}", status.value(), ex.getMessage());
        }

        // Thanks for letting me inspect and polish it, now package it into a proper ResponseEntity and send it on its way.
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }


    @ExceptionHandler(InvalidBikeStatusTransitionException.class)
    public ProblemDetail handleInvalidBikeStatusTransitionException(InvalidBikeStatusTransitionException ex) {
        log.warn("Invalid Bike State Change: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ex.getMessage()
        );
        problem.setTitle("Invalid State Transition");
        return problem;
    }

    @ExceptionHandler(BikeUnderActiveRentalException.class)
    public ProblemDetail handleBikeUnderActiveRentalException(BikeUnderActiveRentalException ex) {
        log.warn("Bike assigned to active rentals: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setProperty("conflicts", ex.getConflicts());
        problem.setTitle("Bike Conflicts");
        return problem;
    }

    /**
     * Fallback handler for any unhandled exceptions.
     * Maps to HTTP 500 Internal Server Error.
     *
     * @param ex the unexpected exception
     * @return a ProblemDetail object explaining the server error generically
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unhandled exception occurred: ", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal server error occurred."
        );
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("about:blank"));

        return problem;
    }
}
