/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.dto;

import io.github.lussac7.taskmanager.domain.Task;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Output DTO for task endpoints. Decouples the API contract from the
 * database schema — changes to {@link Task} don't break API clients.
 *
 * <p>Uses usernames (strings) instead of nested User objects to keep
 * JSON flat and avoid exposing sensitive fields like passwords.</p>
 *
 * @see Task
 * @see CreateTaskRequest (input counterpart)
 */
@Schema(description = "Task data returned by the API")
public class TaskResponse {

    // =========================================================================
    // FIELDS
    // =========================================================================
    // What is a DTO? Data Transfer Object.
    // It's a "view" of the Task entity designed specifically for the API.
    //
    // Why not just return the Task entity directly?
    // 1. SECURITY: The Task entity has relationships (owner, assignedTo) that
    //    contain sensitive data like password hashes. We don't want those
    //    leaking into API responses.
    // 2. STABILITY: If we rename a field in Task.java, the API doesn't break
    //    because we control exactly what appears here.
    // 3. PERFORMANCE: We can choose what to load eagerly vs lazily.

    @Schema(description = "Task unique identifier", example = "1")
    private Long id;

    @Schema(description = "Task title", example = "Complete project report")
    private String title;

    @Schema(description = "Optional description", example = "Write the Q2 summary", nullable = true)
    private String description;

    @Schema(description = "Whether the task is done", example = "false")
    private boolean isComplete;

    @Schema(description = "Username of the task owner", example = "alice")
    private String ownerUsername;

    @Schema(description = "Username of the assigned user", example = "bob", nullable = true)
    private String assignedToUsername;

    @Schema(description = "When the task was created")
    private LocalDateTime createdAt;

    @Schema(description = "Due date of the task", example = "2026-12-31", nullable = true)
    private LocalDate dueDate;

    @Schema(description = "Comma-separated attachment filenames", nullable = true)
    private String attachments;

    // =========================================================================
    // STATIC FACTORY METHOD: from(Task)
    // =========================================================================
    // This is a "static factory method" — a static method that creates and
    // returns a new instance of the class.
    //
    // Why use this instead of a constructor?
    // 1. It has a meaningful name: TaskResponse.from(task) is more readable
    //    than new TaskResponse(task).
    // 2. It's the SINGLE point where domain → DTO conversion happens.
    //    If the Task entity changes, you only fix this ONE method.
    // 3. It can contain logic (like the null check for assignee) that a
    //    simple constructor might miss.

    /**
     * Converts a domain {@link Task} entity into a DTO.
     * This is the SINGLE mapping point — if the entity changes, fix it here.
     *
     * <p><b>Performance note:</b> Dereferencing owner triggers lazy loading.
     * For large lists, use a JOIN FETCH query to avoid the N+1 query problem.</p>
     *
     * @param task the domain entity (owner must not be null)
     * @return a fully populated TaskResponse
     */
    public static TaskResponse from(Task task) {
        // Create an empty response object
        TaskResponse response = new TaskResponse();

        // --- Step 1: Copy scalar fields ---
        // Scalar = simple values (numbers, strings, booleans, dates).
        // These don't involve other entities, so they're a direct copy.
        response.id = task.getId();
        response.title = task.getTitle();
        response.description = task.getDescription();
        response.isComplete = task.isComplete();
        response.createdAt = task.getCreatedAt();
        response.dueDate = task.getDueDate();
        response.attachments = task.getAttachments();

        // --- Step 2: Extract usernames from relationships ---
        // The Task entity has @ManyToOne relationships to User objects.
        // We don't want to expose the full User (with password hash, email, etc.)
        // in the API response. Instead, we extract just the username string.

        // Owner: every task MUST have an owner (NOT NULL in the database).
        // No null check needed. BUT: this line triggers lazy loading.
        // If the owner wasn't already fetched from the database, Hibernate
        // will execute a SELECT query right now to get it.
        response.ownerUsername = task.getOwner().getUsername();

        // Assignee: optional — a task may be unassigned (NULL in the database).
        // If we called getAssignedTo().getUsername() on a null value,
        // we'd get a NullPointerException. So we check first.
        //
        // The "ternary operator" (condition ? valueIfTrue : valueIfFalse)
        // is a compact if/else statement.
        response.assignedToUsername = task.getAssignedTo() != null
                ? task.getAssignedTo().getUsername()   // if assigned → get username
                : null;                                 // if null → stay null

        return response;
    }

    // =========================================================================
    // GETTERS ONLY (No setters)
    // =========================================================================
    // This is an OUTPUT DTO — data flows FROM the server TO the client.
    // The client reads this data but never creates or modifies a TaskResponse.
    //
    // For INPUT, the client uses CreateTaskRequest or AssignTaskRequest.
    //
    // Why no setters?
    // 1. IMMUTABILITY: Once created, a response should not change.
    // 2. THREAD SAFETY: Immutable objects can be safely shared across threads.
    // 3. CLARITY: It's obvious this is a read-only object.

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public boolean isComplete() { return isComplete; }
    public String getOwnerUsername() { return ownerUsername; }
    public String getAssignedToUsername() { return assignedToUsername; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDate getDueDate() { return dueDate; }
    public String getAttachments() { return attachments; }
}