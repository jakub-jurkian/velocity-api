package com.velocity.api.common.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Global exception handler that intercepts exceptions thrown by the application
 * and maps them to standardized RFC 7807 ProblemDetail JSON responses.
 */
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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The provided input data is invalid."
        );
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("about:blank"));
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
    public ProblemDetail handleConflictExceptions(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request could not be completed due to a conflict with the current state of the resource. " + ex.getMessage());
        problem.setTitle("Resource Conflict");
        problem.setType(URI.create("about:blank"));

        return problem;
    }
}
