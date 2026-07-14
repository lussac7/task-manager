/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.repository;

import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link Task} persistence.
 *
 * <p>Custom query methods use Spring Data JPA derivation — the method name
 * is parsed into SQL at startup. No handwritten SQL needed.</p>
 *
 * <p>Methods are organized by return type:
 * <ul>
 *   <li>List&lt;Task&gt; — non-paginated (used by simple queries)</li>
 *   <li>Page&lt;Task&gt; — paginated (used by REST API with ?page=&size=)</li>
 * </ul></p>
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // =========================================================================
    // NON-PAGINATED QUERIES (return List<Task>)
    // =========================================================================
    // These return ALL matching results at once. Use only when you're sure
    // the result set is small, or when you need every result.

    /** All tasks owned by a user. No pagination. */
    List<Task> findAllByOwner(User owner);

    /** Tasks owned by a user, filtered by completion status. No pagination. */
    List<Task> findAllByOwnerAndIsComplete(User owner, boolean isComplete);

    /** How many tasks a user owns. Useful for dashboard statistics. */
    long countByOwner(User owner);

    // =========================================================================
    // PAGINATED QUERIES (return Page<Task>)
    // =========================================================================
    // These accept a Pageable parameter (page, size, sort) and return only
    // one page of results plus metadata (totalElements, totalPages).
    //
    // How Pageable works:
    //   findAllByOwner(user, PageRequest.of(0, 10, Sort.by("createdAt").descending()))
    //   → SELECT * FROM tasks WHERE owner_id = ? ORDER BY created_at DESC LIMIT 10 OFFSET 0
    //
    // The Pageable object is created automatically by Spring from the
    // ?page=0&size=10&sort=createdAt,desc query parameters.

    /** User's tasks with pagination and sorting. */
    Page<Task> findAllByOwner(User owner, Pageable pageable);

    /** Tasks assigned to a specific user, with pagination. */
    Page<Task> findAllByAssignedTo(User assignee, Pageable pageable);

    /** User's tasks filtered by status, with pagination. */
    Page<Task> findAllByOwnerAndIsComplete(User owner, boolean isComplete, Pageable pageable);

    /**
     * Admin operation: filter ALL tasks in the system by completion status.
     * Supports the ?complete=true|false query parameter on admin endpoints.
     *
     * Generated SQL: SELECT * FROM tasks WHERE is_complete = ? LIMIT ? OFFSET ?
     */
    Page<Task> findAllByIsComplete(boolean isComplete, Pageable pageable);
}