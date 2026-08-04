package com.velocity.api.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler that intercepts exceptions thrown by the application
 * and maps them to standardized RFC 7807 ProblemDetail JSON responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Catches validation errors when a request body fails @Valid constraints.
     * Maps to HTTP 400 Bad Request.
     *
     * @param ex the exception containing the validation errors
     * @return a ProblemDetail object with validation failure details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
        log.warn("Validation failed for request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed for one or more fields."
        );
        problem.setTitle("Bad Request");
        problem.setType(URI.create("about:blank"));

        Map<String, String> errors = new HashMap<>();

        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        problem.setProperty("invalidFields", errors);

        return problem;
    }

    /**
     * Catches errors when a requested domain entity is not found in the database.
     * Maps to HTTP 404 Not Found.
     *
     * @param ex the custom exception containing the resource missing message
     * @return a ProblemDetail object with the 404 status
     */
    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public ProblemDetail handleNotFoundException(Exception ex) {
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
