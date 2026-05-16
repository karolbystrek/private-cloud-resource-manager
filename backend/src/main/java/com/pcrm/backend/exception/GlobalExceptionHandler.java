package com.pcrm.backend.exception;

import com.pcrm.backend.idempotency.exception.IdempotencyConflictException;
import com.pcrm.backend.idempotency.exception.IdempotencyInProgressException;
import com.pcrm.backend.idempotency.exception.InvalidIdempotencyKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;

/**
 * Global exception handler following RFC 7807 Problem Details.
 * <p>
 * All error responses are returned as {@code application/problem+json}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Handles bean-validation failures (e.g. @Valid on request bodies).
     * Overrides the default to attach field-level error details.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Validation Error");
        problem.setType(URI.create("about:blank"));

        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Handles resource not found (e.g. profile missing).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Data conflicts with existing records");
        problem.setTitle("Data Conflict");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    @ExceptionHandler(InsufficientQuotaException.class)
    public ProblemDetail handleInsufficientQuota(InsufficientQuotaException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
        problem.setTitle("Payment Required");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ProblemDetail handleSharedInvalidIdempotencyKey(
            InvalidIdempotencyKeyException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Idempotency Key");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleSharedIdempotencyConflict(
            IdempotencyConflictException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Idempotency Conflict");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ProblemDetail handleIdempotencyInProgress(
            IdempotencyInProgressException ex
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Idempotency In Progress");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    @ExceptionHandler(NomadDispatchException.class)
    public ProblemDetail handleNomadDispatch(NomadDispatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Scheduler Dispatch Failed");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Access denied");
        problem.setTitle("Forbidden");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    /**
     * Catch-all for any unhandled exception.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("about:blank"));
        return problem;
    }

    /**
     * Simple record for structured field-level validation errors.
     */
    private record FieldError(String field, String message) {
    }
}
