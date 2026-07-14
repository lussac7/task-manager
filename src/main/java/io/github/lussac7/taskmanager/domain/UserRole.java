/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.domain;

/**
 * User roles for authorization. Stored as VARCHAR in the database.
 *
 * <p>Spring Security converts these to authorities: USER → ROLE_USER, ADMIN → ROLE_ADMIN.
 * A role hierarchy (ROLE_ADMIN > ROLE_USER) gives admins all user permissions.</p>
 *
 * <table>
 *   <tr><th>Role</th><th>Use Cases</th></tr>
 *   <tr><td>{@link #USER}</td><td>UC1 (Create), UC2 (Complete), UC3 (View own), UC5 (Assign)</td></tr>
 *   <tr><td>{@link #ADMIN}</td><td>All USER + UC4 (Delete), UC3 (View all), User management</td></tr>
 * </table>
 *
 * @see User
 * @see io.github.lussac7.taskmanager.config.SecurityConfig
 */
public enum UserRole {

    // =========================================================================
    // VALUES
    // =========================================================================
    // Each role maps to a set of permissions in SecurityConfig.
    // Stored as VARCHAR (EnumType.STRING) — human-readable, safe to reorder.
    //
    // The role hierarchy (ROLE_ADMIN > ROLE_USER) means an admin can access
    // everything a user can, plus admin-only endpoints.

    /**
     * Default role for new accounts.
     * Can create, view, complete, and assign their own tasks.
     * Cannot delete tasks or manage other users.
     */
    USER,

    /**
     * Full system access.
     * Inherits all USER permissions via the role hierarchy.
     * Can delete any task, view all tasks, and manage users (create, role change, delete).
     */
    ADMIN
}