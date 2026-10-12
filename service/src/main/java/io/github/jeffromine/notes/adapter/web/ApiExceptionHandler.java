package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.application.RejectionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Maps the shell's rejections and the framework's own request errors to {@code application/problem+json}.
 * Extending {@link ResponseEntityExceptionHandler} makes Spring's standard errors (unreadable body, missing
 * parameter, wrong method, upload too large) answer in the same shape.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RejectionException.class)
    ProblemDetail rejection(RejectionException e, HttpServletRequest request) {
        return withInstance(Problems.from(e.rejection()), request);
    }

    /** Malformed cursors and out-of-range paging values come from the model's constructors. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        return withInstance(Problems.of(HttpStatus.BAD_REQUEST, "invalid", "Invalid request", e.getMessage()), request);
    }

    /** A blob whose metadata exists but whose bytes are gone. */
    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail noSuchElement(NoSuchElementException e, HttpServletRequest request) {
        return withInstance(Problems.of(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage()), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(Problems.invalid(errors));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Problems.of(HttpStatus.PAYLOAD_TOO_LARGE, "too-large", "Too large", "the upload exceeds the configured size"));
    }

    private static ProblemDetail withInstance(ProblemDetail problem, HttpServletRequest request) {
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
