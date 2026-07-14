/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.service;

import io.github.lussac7.taskmanager.domain.AuditAction;
import io.github.lussac7.taskmanager.domain.AuditEntry;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.repository.AuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records audit events in the database.
 *
 * <p>Uses {@code REQUIRES_NEW} transaction propagation so audit entries
 * survive even if the parent business transaction rolls back.
 * A failed attempt is itself an auditable event.</p>
 *
 * <h2>What is REQUIRES_NEW?</h2>
 * <p>Normally, database operations share a transaction. If the transaction
 * rolls back, EVERYTHING is undone. But audit entries should survive
 * even if the main operation fails. REQUIRES_NEW tells Spring:
 * "Start a SEPARATE transaction just for this method. Commit it immediately,
 * regardless of what happens in the calling method's transaction."</p>
 *
 * @see AuditEntry
 * @see AuditAction
 */
@Service
@Transactional  // Class-level: all public methods run within a transaction
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    // =========================================================================
    // DEPENDENCY
    // =========================================================================
    // Only one dependency — the repository that persists audit entries.
    // Simple, focused, single responsibility.

    private final AuditRepository auditRepository;

    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    // =========================================================================
    // LOG EVENT
    // =========================================================================
    // This is the ONLY public method in this service. It does one thing:
    // creates an AuditEntry and saves it. The AuditEntry constructor
    // automatically sets the timestamp to now.

    /**
     * Records an audit event in the database.
     *
     * <p>The {@code REQUIRES_NEW} propagation means this method always runs
     * in its own transaction. Even if the calling method's transaction fails
     * and rolls back, this audit entry survives.</p>
     *
     * <p><b>Example:</b> If TaskService.markTaskComplete() throws an exception
     * after logging the audit entry, the task state change is rolled back,
     * but the audit entry "COMPLETED" remains. This tells us someone TRIED
     * to complete the task, even though it failed.</p>
     *
     * @param action     what happened (CREATED, COMPLETED, DELETED, ASSIGNED)
     * @param actor      who performed the action
     * @param targetType what kind of object was affected (e.g., "Task")
     * @param targetId   the ID of the affected object
     * @param details    human-readable description of the event
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditAction action, User actor, String targetType,
                         Long targetId, String details) {
        // Create the audit entry. The timestamp is set automatically in the
        // AuditEntry constructor (LocalDateTime.now()).
        AuditEntry entry = new AuditEntry(action, actor, targetType, targetId, details);

        // Persist to the database. This INSERT happens in its own transaction
        // and is committed immediately, regardless of the caller's transaction.
        auditRepository.save(entry);

        // Use debug level because audit entries are normal operations, not errors.
        // In production, set logging to INFO or WARN to reduce noise.
        log.debug("Audit entry saved: {} performed {} on {} #{}",
                actor.getUsername(), action, targetType, targetId);
    }
}