/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.domain;

/**
 * Categories of auditable actions in the system.
 *
 * <p>Stored as {@code VARCHAR} via {@code @Enumerated(EnumType.STRING)}
 * — human-readable and safe against enum reordering.</p>
 *
 * <table>
 *   <tr><th>Constant</th><th>Triggered By</th><th>Use Case</th></tr>
 *   <tr><td>{@link #CREATED}</td><td>{@code TaskService.createTask()}</td><td>UC1</td></tr>
 *   <tr><td>{@link #COMPLETED}</td><td>{@code TaskService.markTaskComplete()}</td><td>UC2</td></tr>
 *   <tr><td>{@link #DELETED}</td><td>{@code TaskService.deleteTask()}</td><td>UC4</td></tr>
 *   <tr><td>{@link #ASSIGNED}</td><td>{@code TaskService.assignTaskToUser()}</td><td>UC5</td></tr>
 * </table>
 *
 * @see AuditEntry
 * @see io.github.lussac7.taskmanager.service.AuditService
 */
public enum AuditAction {

    // =========================================================================
    // VALUES
    // =========================================================================
    // Each value corresponds to one of the four write operations in the system.
    // When TaskService performs an action, it calls auditService.logEvent()
    // with the appropriate enum value.
    //
    // Why STRING and not ORDINAL?
    // - STRING stores "CREATED" in the database (readable, safe to reorder)
    // - ORDINAL stores 0, 1, 2, 3 (compact, but breaks if you reorder enums)
    // Always use STRING for database enums.

    /** Task created by a user. Triggered by TaskService.createTask(). */
    CREATED,

    /** Task marked as done. Cannot be undone. Triggered by TaskService.markTaskComplete(). */
    COMPLETED,

    /** Task permanently removed. Admin only. Triggered by TaskService.deleteTask(). */
    DELETED,

    /** Task assigned to a specific user. Triggered by TaskService.assignTaskToUser(). */
    ASSIGNED
}