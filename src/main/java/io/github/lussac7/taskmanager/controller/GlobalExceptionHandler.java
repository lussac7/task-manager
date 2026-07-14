/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.controller;

import io.github.lussac7.taskmanager.dto.AppResponse;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Translates exceptions from REST controllers into standardized JSON error responses.
 *
 * <p>Only intercepts exceptions from {@code @RestController} beans
 * ({@link TaskController}, {@link AdminController}, {@link UserController}).
 * Thymeleaf/SPA page errors use Spring Boot's default HTML error handling.</p>
 *
 * <p><b>Exception → HTTP mapping:</b></p>
 * <table>
 *   <tr><th>Exception</th><th>HTTP Status</th></tr>
 *   <tr><td>{@link EntityNotFoundException}</td><td>404 Not Found</td></tr>
 *   <tr><td>{@link MethodArgumentNotValidException}</td><td>400 Bad Request</td></tr>
 *   <tr><td>{@link Exception} (catch-all)</td><td>500 Internal Server Error</td></tr>
 * </table>
 */
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // =========================================================================
    // 404 NOT FOUND
    // =========================================================================
    // Thrown when a repository's findById() returns Optional.empty().
    // Example: taskService.markTaskComplete(999) → "Task not found: 999".
    // Maps to the alt [task not found] fragment in the Sequence Diagrams.

    /** Maps to Sequence Diagram alt [task not found] fragment. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<AppResponse<Void>> handleNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(AppResponse.error(ex.getMessage()));
    }

    // =========================================================================
    // 400 BAD REQUEST (Validation)
    // =========================================================================
    // Triggered when @Valid fails on a request body.
    // Example: creating a task with a blank title → "Title is required".
    // We collect ALL field errors into a single message so the client
    // knows everything that needs fixing in one response.

    /** Triggered when @Valid fails on a request body (e.g., blank title, null taskId). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AppResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        // Collect all field errors into a single comma-separated message.
        // Example output: "title: Title is required, description: Description must be under 1000 characters"
        String errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(AppResponse.error("Validation failed: " + errors));
    }

    // =========================================================================
    // 500 INTERNAL SERVER ERROR (Catch-all)
    // =========================================================================
    // Any exception not caught by the handlers above ends up here.
    // We log the FULL stack trace for debugging, but return a generic
    // message to the client (don't leak internal details in production).

    /** Catch-all for unexpected errors. Logs full stack trace for debugging. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AppResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error in REST endpoint", ex);  // Full stack trace in logs
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AppResponse.error("An unexpected error occurred"));  // Generic message to client
    }
}