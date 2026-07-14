/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.controller;

import io.github.lussac7.taskmanager.config.CurrentUserArgumentResolver;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.dto.*;
import io.github.lussac7.taskmanager.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for task CRUD operations available to authenticated users.
 *
 * <p>All endpoints require {@code ROLE_USER} (or higher via role hierarchy).
 * Admin-specific operations live in {@link AdminController}.</p>
 *
 * <p><b>Base path:</b> {@code /api/tasks}</p>
 * <p><b>Use Cases:</b> UC1 (Create), UC2 (Complete), UC3 (View), UC5 (Assign)</p>
 * <p><b>Auth:</b> Current user resolved by {@link CurrentUserArgumentResolver}.</p>
 *
 * @see AdminController (admin counterpart)
 * @see TaskService
 */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Task management endpoints for authenticated users")
@SecurityRequirement(name = "BasicAuth")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    // =========================================================================
    // DEPENDENCY
    // =========================================================================
    // TaskController depends on TaskService. Spring injects it via the
    // constructor. The @AuthenticationPrincipal parameter on each method
    // is resolved by CurrentUserArgumentResolver.

    /** Service layer for task business logic. Injected via constructor. */
    private final TaskService taskService;

    /** @param taskService the task management service */
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // =========================================================================
    // UC1: Create a Task
    // =========================================================================
    // POST /api/tasks
    // The user sends a JSON body with title (required), description (optional),
    // and dueDate (optional). The task is persisted and an audit entry is created.

    /**
     * Creates a new task owned by the authenticated user.
     *
     * <p><b>Validation:</b> title is required (3–100 chars), description is optional (max 1000).</p>
     *
     * @param user    authenticated user (becomes task owner)
     * @param request task data (title required, description optional)
     * @return {@code 201 Created} with the new task
     */
    @Operation(summary = "Create a new task", description = "Creates a task owned by the authenticated user.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PostMapping
    public ResponseEntity<AppResponse<TaskResponse>> createTask(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateTaskRequest request) {

        log.info("Creating task for user: {}", user.getUsername());
        // Delegates to TaskService which:
        // 1. Creates a Task domain object
        // 2. Persists it to the database (JPA assigns the ID)
        // 3. Logs an audit entry (AuditAction.CREATED)
        var task = taskService.createTask(user, request.getTitle(), request.getDescription(), request.getDueDate());
        return ResponseEntity
                .status(HttpStatus.CREATED)  // 201 — resource created
                .body(AppResponse.success("Task created", TaskResponse.from(task)));
    }

    // =========================================================================
    // UC2: Mark Task as Complete
    // =========================================================================
    // PATCH /api/tasks/{id}/complete
    // Implements the full "Mark Task as Complete" Sequence Diagram:
    // find → markComplete() → notify (if enabled) → audit → persist

    /**
     * Marks a task as complete. Once completed, a task cannot be un-completed.
     *
     * @param id the task ID to complete
     * @return {@code 200 OK}, or {@code 404} if not found
     */
    @Operation(summary = "Mark task as complete", description = "Marks a task as done. Cannot be undone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task marked complete"),
            @ApiResponse(responseCode = "404", description = "Task not found"),
            @ApiResponse(responseCode = "409", description = "Task already completed")
    })
    @PatchMapping("/{id}/complete")
    public ResponseEntity<AppResponse<Void>> markTaskComplete(
            @Parameter(description = "ID of the task to complete", example = "100")
            @PathVariable Long id) {
        log.info("Marking task complete: id={}", id);
        // Delegates the full Sequence Diagram flow to TaskService:
        // Step 1: findById → Step 2: markComplete() → Step 3: notify →
        // Step 4: audit → Step 5: persist
        taskService.markTaskComplete(id);
        return ResponseEntity.ok(AppResponse.success("Task marked as complete"));
    }

    // =========================================================================
    // UC3: View Tasks (User Scope, Paginated, Filterable, Sortable)
    // =========================================================================
    // GET /api/tasks?page=0&size=10&sort=createdAt,desc&complete=false
    // Returns ONLY the authenticated user's tasks (not all tasks).

    /**
     * Returns the authenticated user's tasks with optional filtering, sorting, and pagination.
     *
     * <p><b>Query parameters:</b>
     * <ul>
     *   <li>{@code complete} — filter: {@code true}=completed, {@code false}=pending, absent=all</li>
     *   <li>{@code page} — 0-based page number (default 0)</li>
     *   <li>{@code size} — items per page (default 10)</li>
     *   <li>{@code sort} — property and direction, e.g. {@code createdAt,desc} (default)</li>
     * </ul>
     *
     * @param user     authenticated user
     * @param complete optional status filter
     * @param pageable pagination and sorting
     * @return paginated, optionally filtered task list
     */
    @Operation(
            summary = "View user's tasks",
            description = "Returns the authenticated user's tasks with optional status filter, pagination, and sorting."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tasks retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping
    public ResponseEntity<AppResponse<PagedResponse<TaskResponse>>> viewAllTasks(
            @AuthenticationPrincipal User user,
            @Parameter(description = "Filter: true=completed, false=pending, absent=all")
            @RequestParam(required = false) Boolean complete,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.info("Viewing tasks for user: {} | complete: {} | page: {} | size: {}",
                user.getUsername(), complete, pageable.getPageNumber(), pageable.getPageSize());

        // The service decides which repository query to run:
        // - complete=null → findAllByOwner(user) — no filter
        // - complete=true/false → findByOwnerAndStatus(user, complete) — filtered
        Page<TaskResponse> taskPage = taskService
                .findAllTasksByUserAndStatus(user, complete, pageable)
                .map(TaskResponse::from);  // Convert each Task entity → TaskResponse DTO

        return ResponseEntity.ok(
                AppResponse.success("Tasks retrieved", PagedResponse.from(taskPage)));
    }

    // =========================================================================
    // UC5: Assign Task to User
    // =========================================================================
    // PATCH /api/tasks/assign
    // Implements the full "Assign Task to User" Sequence Diagram:
    // find task → find assignee → assign → audit → notify → persist

    /**
     * Assigns a task to a specific user.
     *
     * @param request contains {@code taskId} and {@code userId} (both required)
     * @return {@code 200 OK}, or {@code 404} if task/user not found
     */
    @Operation(summary = "Assign task to user", description = "Assigns an existing task to a specific user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task assigned"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Task or user not found")
    })
    @PatchMapping("/assign")
    public ResponseEntity<AppResponse<Void>> assignTaskToUser(
            @Valid @RequestBody AssignTaskRequest request) {

        log.info("Assigning task {} to user {}", request.getTaskId(), request.getUserId());
        // Delegates the full Sequence Diagram flow to TaskService:
        // Step 1: find task → Step 2: find assignee → Step 3: assign →
        // Step 4: audit → Step 5: notify → Step 6: persist
        taskService.assignTaskToUser(request.getTaskId(), request.getUserId());
        return ResponseEntity.ok(AppResponse.success("Task assigned successfully"));
    }
}