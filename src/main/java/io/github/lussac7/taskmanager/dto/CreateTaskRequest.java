/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request body for {@code POST /api/tasks}.
 *
 * <p><b>Validation:</b> title is required (3–100 chars), description is optional (max 1000).
 * Validation runs BEFORE the controller method executes. Errors return 400 with field details.</p>
 *
 * @see AssignTaskRequest
 */
@Schema(description = "Request body for creating a new task")
public class CreateTaskRequest {

    // =========================================================================
    // FIELDS
    // =========================================================================
    // Validation annotations are checked by Spring BEFORE the controller method
    // runs. If validation fails, GlobalExceptionHandler returns 400 with details.

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
    @Schema(description = "Task title", example = "Complete project report", minLength = 3, maxLength = 100)
    private String title;

    @Size(max = 1000, message = "Description must be under 1000 characters")
    @Schema(description = "Optional task description", example = "Write the Q2 summary", maxLength = 1000, nullable = true)
    private String description;

    @Schema(description = "Optional due date (ISO format: YYYY-MM-DD)", example = "2026-12-31", nullable = true)
    private LocalDate dueDate;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * No-arg constructor required by Jackson for JSON deserialization.
     * Jackson creates an empty instance, then calls setters for each JSON field.
     */
    public CreateTaskRequest() {
    }

    /**
     * Convenience constructor for tests (due date is not typically set in tests).
     */
    public CreateTaskRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }
}