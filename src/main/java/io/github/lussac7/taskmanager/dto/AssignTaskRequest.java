/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PATCH /api/tasks/assign}.
 *
 * <p>Both fields are required — the service throws {@code EntityNotFoundException}
 * if either ID doesn't exist in the database.</p>
 *
 * @see CreateTaskRequest
 */
@Schema(description = "Request body for assigning a task to a user")
public class AssignTaskRequest {

    // =========================================================================
    // FIELDS
    // =========================================================================
    // Both fields use @NotNull (not @NotBlank) because they are Long values,
    // not Strings. @NotNull means "the JSON must include this field."
    // @NotBlank is only for Strings.

    @NotNull(message = "Task ID is required")
    @Schema(description = "ID of the task to assign", example = "100")
    private Long taskId;

    @NotNull(message = "User ID is required")
    @Schema(description = "ID of the user to receive the assignment", example = "42")
    private Long userId;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * No-arg constructor required by Jackson for JSON deserialization.
     * Jackson creates an empty instance first, then calls the setters
     * for each field found in the JSON body. Without this constructor,
     * deserialization would fail.
     */
    public AssignTaskRequest() {}

    /** Convenience constructor for tests and programmatic use. */
    public AssignTaskRequest(Long taskId, Long userId) {
        this.taskId = taskId;
        this.userId = userId;
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}