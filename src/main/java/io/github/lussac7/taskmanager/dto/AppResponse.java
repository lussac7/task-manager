/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Standardized wrapper for every API response.
 *
 * <p>All responses follow a single structure: {@code success}, {@code message},
 * {@code data}, and {@code timestamp}. Use static factories — never call
 * {@code new AppResponse()} directly.</p>
 *
 * <pre>{@code
 * AppResponse.success("Tasks retrieved", taskList);   // 200 with data
 * AppResponse.success("Task deleted");                // 200 without data
 * AppResponse.error("Task not found: 999");           // 4xx/5xx
 * }</pre>
 *
 * @param <T> payload type: {@code TaskResponse}, {@code List<TaskResponse>}, or {@code Void}
 */
@Schema(description = "Standard API response wrapper")
public class AppResponse<T> {

    // =========================================================================
    // FIELDS
    // =========================================================================
    // All fields are final — once created, a response never changes.
    // This makes AppResponse thread-safe and predictable.

    @Schema(description = "Whether the request succeeded", example = "true")
    private final boolean success;

    @Schema(description = "Human-readable result message", example = "Task created")
    private final String message;

    @Schema(description = "Response payload (may be null)", nullable = true)
    private final T data;

    @Schema(description = "Server timestamp of the response")
    private final LocalDateTime timestamp;

    // =========================================================================
    // CONSTRUCTOR (PRIVATE)
    // =========================================================================
    // You can't call "new AppResponse()" from outside this class.
    // This forces everyone to use the static factory methods below.
    // Why? It prevents invalid combinations like success=true with an error message.

    /** Private — use static factories. */
    private AppResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();  // Auto-set to current server time
    }

    // =========================================================================
    // STATIC FACTORY METHODS
    // =========================================================================
    // These are the ONLY way to create an AppResponse.
    // Each method name clearly communicates intent (success vs error).

    /**
     * Creates a success response with a data payload.
     * Use for GET endpoints that return data, or POST endpoints that return
     * the created resource.
     * Example: AppResponse.success("Tasks retrieved", taskList)
     */
    public static <T> AppResponse<T> success(String message, T data) {
        return new AppResponse<>(true, message, data);
    }

    /**
     * Creates a success response without a data payload.
     * Use for DELETE and PATCH endpoints that perform an action but return
     * no data.
     * Example: AppResponse.success("Task deleted")
     */
    public static <T> AppResponse<T> success(String message) {
        return new AppResponse<>(true, message, null);
    }

    /**
     * Creates an error response.
     * Use in exception handlers (GlobalExceptionHandler) to return consistent
     * error structures.
     * Example: AppResponse.error("Task not found: 999")
     */
    public static <T> AppResponse<T> error(String message) {
        return new AppResponse<>(false, message, null);
    }

    // =========================================================================
    // GETTERS (NO SETTERS — immutable)
    // =========================================================================

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public LocalDateTime getTimestamp() { return timestamp; }
}