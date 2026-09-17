package com.velocity.api.common.exception;

import com.velocity.api.bike.exception.BikeNotAvailableException;
import com.velocity.api.bike.exception.InvalidBikeStateException;
import com.velocity.api.bike.exception.InvalidBikeStatusTransitionException;
import com.velocity.api.reservation.exception.InvalidStatusTransitionException;
import com.velocity.api.reservation.exception.LateCancelException;
import com.velocity.api.user.exception.CannotDemoteSelfException;
import com.velocity.api.user.exception.EmailAlreadyRegisteredException;
import com.velocity.api.user.exception.InvalidUserStateException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
        log.warn("Registration failed - Conflict: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                ex.getMessage()
        );
        problem.setTitle("Resource Conflict");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    /**
     * Catches database-level integrity violations, inspecting the root cause
     * to provide specific conflict messages for reservation overlaps or unique constraints.
     * Maps to HTTP 409 Conflict.
     *
     * @param ex the data integrity violation exception
     * @return a ProblemDetail object explaining the specific conflict
     */
    @ExceptionHandler({DataIntegrityViolationException.class, CannotAcquireLockException.class})
    public ProblemDetail handleDataIntegrityViolationException(DataAccessException ex) {
        log.warn("Database integrity violation occurred: {}", ex.getMessage());

        String detail = "A database conflict occurred.";
        String title = "Resource Conflict";

        // Check if the exception message stems from our reservation exclusion constraint (ADR-001)
        String rootMessage = ex.getMostSpecificCause().getMessage();
        if (rootMessage != null && rootMessage.contains("no_overlapping_active_reservations")) {
            detail = "This bike is already reserved for the selected dates.";
            title = "Bike Not Available";
        } else if (rootMessage != null && rootMessage.contains("email")) {
            detail = "An account with this email already exists.";
            title = "Duplicate Record";
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                detail
        );
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));

        return problem;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid arguments: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
        );
        problem.setTitle("Invalid arguments");
        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentialsException(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ex.getMessage()
        );
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
                HttpStatus.BAD_REQUEST,
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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleAObjectOptimisticLockingFailureException(ObjectOptimisticLockingFailureException ex) {
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
