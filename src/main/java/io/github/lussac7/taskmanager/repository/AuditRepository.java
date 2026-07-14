/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.repository;

import io.github.lussac7.taskmanager.domain.AuditAction;
import io.github.lussac7.taskmanager.domain.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for {@link AuditEntry} — append-only audit log.
 * Entries are never updated or deleted, only inserted and read.
 *
 * <p>Query methods use Spring Data JPA derivation — the method name
 * becomes the SQL query at startup. No SQL or JPQL needed.</p>
 *
 * @see AuditEntry
 * @see AuditAction
 */
@Repository
public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    // =========================================================================
    // QUERY METHODS
    // =========================================================================
    // How does Spring Data JPA turn a method name into SQL?
    //
    // Example: findAllByAction(AuditAction action)
    //   findAll  → SELECT * FROM audit_entries
    //   ByAction → WHERE action = ?
    //
    // Example: findAllByActorId(Long actorId)
    //   findAll   → SELECT * FROM audit_entries
    //   ByActorId → WHERE actor_id = ?
    //
    // Example: findTop10ByOrderByTimestampDesc()
    //   findTop10                   → SELECT * ... LIMIT 10
    //   ByOrderByTimestampDesc      → ORDER BY timestamp DESC
    //
    // This is called "query derivation" — Spring parses the method name
    // at application startup and generates the SQL. If you follow the
    // naming convention, you never need to write SQL by hand.

    /** All entries of a given action type (e.g., COMPLETED). */
    List<AuditEntry> findAllByAction(AuditAction action);

    /** All actions performed by a specific user. */
    List<AuditEntry> findAllByActorId(Long actorId);

    /** Full audit history for a specific object (e.g., Task #101). */
    List<AuditEntry> findAllByTargetTypeAndTargetId(String targetType, Long targetId);

    /** Entries within a date range. */
    List<AuditEntry> findAllByTimestampBetween(LocalDateTime start, LocalDateTime end);

    /** Most recent 10 entries (for a dashboard widget). */
    List<AuditEntry> findTop10ByOrderByTimestampDesc();
}