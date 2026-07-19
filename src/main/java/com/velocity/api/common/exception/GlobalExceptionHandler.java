package com.velocity.api.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
     * Catches business logic state conflicts and database integrity violations.
     * Maps to HTTP 409 Conflict.
     *
     * @param ex the exception representing the clash in business rules or data constraints
     * @return a ProblemDetail object explaining the resource conflict
     */
    @ExceptionHandler({IllegalStateException.class, DataIntegrityViolationException.class})
    public ProblemDetail handleConflictException(RuntimeException ex) {
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
