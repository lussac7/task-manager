/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.controller;

import io.github.lussac7.taskmanager.dto.AppResponse;
import io.github.lussac7.taskmanager.dto.PagedResponse;
import io.github.lussac7.taskmanager.dto.TaskResponse;
import io.github.lussac7.taskmanager.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only REST endpoints for task management.
 *
 * <p>All endpoints require {@code ROLE_ADMIN}. Authorization is enforced by
 * {@link io.github.lussac7.taskmanager.config.SecurityConfig}
 * before any request reaches this controller.</p>
 *
 * <p><b>Base path:</b> {@code /api/admin/tasks}</p>
 * <p><b>Use Cases:</b> UC3 (View All Tasks — admin scope), UC4 (Delete a Task)</p>
 *
 * @see TaskController (user-facing counterpart)
 * @see TaskService
 */
@RestController
@RequestMapping("/api/admin/tasks")
@Tag(name = "Admin", description = "Admin-only task operations (requires ROLE_ADMIN)")
@SecurityRequirement(name = "BasicAuth")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    // =========================================================================
    // DEPENDENCY
    // =========================================================================
    // AdminController depends on TaskService (the same service used by
    // TaskController). The difference is in the authorization rules:
    // SecurityConfig requires ROLE_ADMIN for /api/admin/** endpoints.

    /** Service layer for task business logic. Injected via constructor. */
    private final TaskService taskService;

    /**
     * @param taskService the task management service
     */
    public AdminController(TaskService taskService) {
        this.taskService = taskService;
    }

    // =========================================================================
    // UC4: Delete a Task (Admin Only)
    // =========================================================================
    // This is the ONLY endpoint that regular users cannot access.
    // Spring Security blocks the request before it reaches this method
    // if the user doesn't have ROLE_ADMIN.

    /**
     * Permanently deletes a task. Admin only.
     *
     * <p>The audit entry is recorded <b>before</b> deletion so the task
     * title is preserved in the audit log.</p>
     *
     * @param id the task ID to delete
     * @return {@code 200 OK} with success message, or {@code 404} if not found
     */
    @Operation(summary = "Delete a task",
            description = "Permanently removes a task from the system. Requires ROLE_ADMIN.")
    @ApiResponse(responseCode = "200", description = "Task deleted")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Requires ROLE_ADMIN")
    @ApiResponse(responseCode = "404", description = "Task not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<AppResponse<Void>> deleteTask(
            @Parameter(description = "ID of the task to delete", example = "100")
            @PathVariable Long id) {
        log.info("Admin deleting task: id={}", id);
        // Delegates to TaskService which:
        // 1. Finds the task by ID (throws if not found)
        // 2. Records an audit entry BEFORE deletion (so we know who deleted what)
        // 3. Removes the task from the database
        taskService.deleteTask(id);
        return ResponseEntity.ok(AppResponse.success("Task deleted"));
    }

    // =========================================================================
    // UC3: View All Tasks (Admin Scope)
    // =========================================================================
    // Unlike the user endpoint (GET /api/tasks), this returns tasks from
    // ALL users in the system, not just the authenticated user's tasks.

    /**
     * Returns all tasks in the system with optional filtering and pagination.
     *
     * <p><b>Query parameters:</b>
     * <ul>
     *   <li>{@code complete} — filter: {@code true}=completed, {@code false}=pending, absent=all</li>
     *   <li>{@code page} — 0-based page number (default 0)</li>
     *   <li>{@code size} — items per page (default 10)</li>
     *   <li>{@code sort} — property and direction, e.g. {@code createdAt,desc} (default)</li>
     * </ul>
     *
     * @param complete filter: {@code true} = completed, {@code false} = pending,
     *                 absent = all tasks
     * @param pageable pagination and sorting
     * @return paginated list of all matching tasks
     */
    @Operation(summary = "View all tasks (admin scope)",
            description = "Returns all tasks in the system with optional status filter and pagination.")
    @ApiResponse(responseCode = "200", description = "Tasks retrieved")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Requires ROLE_ADMIN")
    @GetMapping
    public ResponseEntity<AppResponse<PagedResponse<TaskResponse>>> viewAllTasks(
            @Parameter(description = "Filter: true=completed, false=pending, absent=all")
            @RequestParam(required = false) Boolean complete,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.info("Admin viewing all tasks | complete: {} | page: {} | size: {}",
                complete, pageable.getPageNumber(), pageable.getPageSize());

        // The service decides which repository query to run:
        // - complete=null → findAll() (all tasks, no filter)
        // - complete=true → findByStatus(true) (completed only)
        // - complete=false → findByStatus(false) (pending only)
        Page<TaskResponse> taskPage = taskService
                .findAllTasks(complete, pageable)
                .map(TaskResponse::from);  // Convert each Task entity → TaskResponse DTO

        return ResponseEntity.ok(
                AppResponse.success("All tasks retrieved", PagedResponse.from(taskPage)));
    }
}