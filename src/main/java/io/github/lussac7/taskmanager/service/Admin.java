/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.service;

import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.domain.User;

import java.util.List;
import java.util.Objects;

/**
 * Behavioral wrapper around a {@link User} with ADMIN role.
 * NOT a JPA entity — delegates admin operations to {@link TaskService}.
 *
 * <p>This avoids JPA inheritance complexity (no separate admins table).
 * Admin is a ROLE, not a separate entity.</p>
 *
 * <h2>Why not make Admin a JPA entity?</h2>
 * <p>In the Class Diagram, Admin inherits from User. But in JPA, inheritance
 * means choosing a strategy (SINGLE_TABLE, JOINED, TABLE_PER_CLASS), each
 * with trade-offs. Since Admin has NO unique persisted attributes (only
 * extra methods), a separate table is overkill. Instead, Admin is a
 * behavioral wrapper that holds a User (with role=ADMIN) and a TaskService,
 * delegating all operations.</p>
 */
public class Admin {

    // =========================================================================
    // FIELDS
    // =========================================================================
    // Admin wraps a regular User entity and a TaskService.
    // The User MUST have role = ADMIN for this to make sense.
    // The TaskService is used to execute admin-specific operations.

    private final User user;
    private final TaskService taskService;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    /**
     * Creates an Admin wrapper.
     *
     * @param user        the User entity (should have ADMIN role, but this is
     *                    not enforced here — authorization is handled by Spring Security)
     * @param taskService the service for task operations
     * @throws NullPointerException if either argument is null (enforced by Objects.requireNonNull)
     */
    public Admin(User user, TaskService taskService) {
        // Objects.requireNonNull throws NullPointerException immediately if the
        // argument is null. This is a "fail-fast" approach — better to catch
        // a null reference at construction time than later when a method is called.
        this.user = Objects.requireNonNull(user, "User must not be null");
        this.taskService = Objects.requireNonNull(taskService, "TaskService must not be null");
    }

    // =========================================================================
    // ADMIN OPERATIONS (Delegated to TaskService)
    // =========================================================================
    // Each method is a thin wrapper that simply calls the corresponding
    // TaskService method. The Admin class itself contains no business logic.
    // This is the "Facade" pattern — Admin provides a simplified interface
    // to the underlying service.

    /** UC4: Delete any task in the system. Admin only. */
    public void deleteTask(Long taskId) {
        taskService.deleteTask(taskId);
    }

    /** UC5: Assign a task to a specific user. */
    public void assignTaskToUser(Long taskId, Long userId) {
        taskService.assignTaskToUser(taskId, userId);
    }

    /** UC3 (admin scope): View ALL tasks, not just the admin's own tasks. */
    public List<Task> viewAllTasks() {
        return taskService.findAllTasks();
    }

    // =========================================================================
    // DELEGATED GETTERS
    // =========================================================================
    // Expose the wrapped User's properties without exposing the User object
    // directly (except via getUser() when needed for persistence).

    public Long getId() { return user.getId(); }
    public String getUsername() { return user.getUsername(); }
    public String getEmail() { return user.getEmail(); }

    /** Returns the underlying User entity. Use for persistence operations. */
    public User getUser() { return user; }

    // =========================================================================
    // EQUALS & HASHCODE
    // =========================================================================
    // Two Admin objects are equal if they wrap the same User (same ID).

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Admin admin)) return false;
        return Objects.equals(user.getId(), admin.user.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(user.getId());
    }

    @Override
    public String toString() {
        return "Admin{userId=" + user.getId() + ", username='" + user.getUsername() + "'}";
    }
}