/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.service;

import io.github.lussac7.taskmanager.domain.AuditAction;
import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.repository.TaskRepository;
import io.github.lussac7.taskmanager.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Core service for task management — orchestrates repositories, audit, and notifications.
 *
 * <p>Every public method corresponds to a Use Case and follows its Sequence Diagram.
 * Class-level {@code @Transactional} ensures all operations within a method succeed
 * or fail as a unit.</p>
 *
 * <h2>What is @Transactional?</h2>
 * <p>It wraps every public method in a database transaction. If any operation
 * inside the method throws a RuntimeException, EVERYTHING is rolled back.
 * Example: if audit logging fails after marking a task complete, the task
 * state change is also undone. This keeps the database consistent.</p>
 *
 * @see Task
 * @see AuditService
 * @see NotificationService
 */
@Service
@Transactional
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    /** Centralized error message prefix for consistency. */
    private static final String TASK_NOT_FOUND = "Task not found: ";

    // =========================================================================
    // DEPENDENCIES (Injected via constructor)
    // =========================================================================
    // Four dependencies, each with a clear responsibility:
    // - TaskRepository: database operations for tasks
    // - UserRepository: look up users (needed for assignTaskToUser)
    // - AuditService: record what happened (cross-cutting concern)
    // - NotificationService: notify users (cross-cutting concern)

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Autowired
    public TaskService(TaskRepository taskRepository,
                       UserRepository userRepository,
                       AuditService auditService,
                       NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    // =========================================================================
    // UC1: Create a Task
    // =========================================================================

    /**
     * Creates a task, persists it, and logs an audit entry.
     *
     * <p>The flow is simple:
     * <ol>
     *   <li>Create a Task domain object (Java object, not yet in database)</li>
     *   <li>Save it (JPA generates INSERT, database assigns ID)</li>
     *   <li>Log the creation in the audit log</li>
     *   <li>Return the task with its new database ID</li>
     * </ol></p>
     *
     * @param user        the task owner (who created it)
     * @param title       short description
     * @param description optional details
     * @param dueDate     optional deadline
     * @return the persisted task with its database-assigned ID
     */
    public Task createTask(User user, String title, String description, LocalDate dueDate) {
        // Step 1: Create the domain object (transient — not yet in database)
        Task task = new Task(title, description, user);
        task.setDueDate(dueDate);

        // Step 2: Persist to database. JPA generates INSERT, database assigns the ID.
        // The returned "saved" object has the ID populated.
        Task saved = taskRepository.save(task);

        // Step 3: Record the creation in the audit log.
        // Uses REQUIRES_NEW transaction — survives even if this transaction fails.
        auditService.logEvent(AuditAction.CREATED, user, "Task", saved.getId(),
                "Task created: " + title);

        log.info("Task created: id={}, title={}, owner={}, dueDate={}",
                saved.getId(), title, user.getUsername(), dueDate);
        return saved;
    }

    /** Backward compatibility — creates a task without a due date. */
    public Task createTask(User user, String title, String description) {
        return createTask(user, title, description, null);
    }

    // =========================================================================
    // UC2: Mark Task as Complete
    // =========================================================================

    /**
     * Marks a task as complete.
     * Follows the "Mark Task as Complete" Sequence Diagram step by step.
     *
     * <p><b>Sequence Diagram → Code mapping:</b>
     * <pre>
     * TS -> TR : findById(101)     → taskRepository.findById(taskId)
     * TS -> T  : markComplete()    → task.markComplete()
     * T  -> T  : setComplete(true) → (inside Task.markComplete)
     * opt [notifications enabled]  → if (owner.isNotificationEnabled())
     * T  -> NS : sendNotification  → notificationService.sendNotification(task)
     * TS -> AS : logEvent          → auditService.logEvent(COMPLETED, ...)
     * TS -> TR : save              → taskRepository.save(task)
     * </pre></p>
     *
     * @param taskId the ID of the task to complete
     * @throws EntityNotFoundException if the task doesn't exist
     */
    public void markTaskComplete(Long taskId) {
        // --- Step 1: Find the task ---
        // If not found, throw EntityNotFoundException → GlobalExceptionHandler → 404
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException(TASK_NOT_FOUND + taskId));

        // --- Step 2: Execute domain logic ---
        // Delegates to the Task entity, which enforces the business rule
        // (can only complete once, throws IllegalStateException if already done)
        task.markComplete();

        // --- Step 3: Conditional notification ---
        // The guard condition from the Sequence Diagram: opt [isNotificationEnabled()]
        // Only notify if the owner has opted in to notifications.
        User owner = task.getOwner();
        if (owner.isNotificationEnabled()) {
            notificationService.sendNotification(task);
        }

        // --- Step 4: Audit the event ---
        // Records WHO completed WHICH task and WHEN.
        // Uses REQUIRES_NEW — survives even if the save below fails.
        auditService.logEvent(AuditAction.COMPLETED, owner, "Task", taskId,
                "Task marked complete");

        // --- Step 5: Persist the state change ---
        // Hibernate's dirty-checking detects isComplete changed and generates UPDATE.
        taskRepository.save(task);
        log.info("Task marked complete: id={}", taskId);
    }

    // =========================================================================
    // UC4: Delete a Task (Admin Only)
    // =========================================================================

    /**
     * Permanently deletes a task.
     * The audit entry is recorded BEFORE deletion to preserve the task title.
     *
     * <p><b>Why audit before delete?</b> After taskRepository.delete(task),
     * the task object still exists in memory, but calling task.getTitle()
     * would still work. However, it's a best practice to audit first —
     * in case the entity manager clears the object after deletion.</p>
     *
     * @param taskId the ID of the task to delete
     * @throws EntityNotFoundException if the task doesn't exist
     */
    public void deleteTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException(TASK_NOT_FOUND + taskId));

        User owner = task.getOwner();

        // Audit BEFORE deletion — so the audit entry can include the task title.
        // After deletion, the row is gone from the database, but the audit entry
        // still references the old task ID (audit_entries.target_id).
        auditService.logEvent(AuditAction.DELETED, owner, "Task", taskId,
                "Task deleted: " + task.getTitle());

        // Delete from database. The Composition relationship (cascade=ALL,
        // orphanRemoval=true) on User.ownedTasks would also handle this if we
        // removed from the owner's collection, but explicit delete is clearer.
        taskRepository.delete(task);
        log.info("Task deleted: id={}", taskId);
    }

    // =========================================================================
    // UC3: View Tasks (Multiple overloads for different use cases)
    // =========================================================================
    // readOnly = true is a Hibernate optimization: it skips "dirty checking"
    // (tracking changes to loaded entities). This makes queries faster.
    // NEVER use readOnly = true on methods that need to save changes!

    /** All tasks for a user. Non-paginated. */
    @Transactional(readOnly = true)
    public List<Task> findAllTasksByUser(User user) {
        return taskRepository.findAllByOwner(user);
    }

    /** User's tasks with pagination. Used by REST API. */
    @Transactional(readOnly = true)
    public Page<Task> findAllTasksByUser(User user, Pageable pageable) {
        return taskRepository.findAllByOwner(user, pageable);
    }

    /** All tasks in the system. Non-paginated. */
    @Transactional(readOnly = true)
    public List<Task> findAllTasks() {
        return taskRepository.findAll();
    }

    /** All tasks with pagination. Used by admin REST API. */
    @Transactional(readOnly = true)
    public Page<Task> findAllTasks(Pageable pageable) {
        return taskRepository.findAll(pageable);
    }

    // =========================================================================
    // UC5: Assign Task to User
    // =========================================================================

    /**
     * Assigns a task to a user.
     * Follows the "Assign Task to User" Sequence Diagram step by step.
     *
     * <p><b>Fail-fast pattern:</b> Both lookups (task + user) happen BEFORE
     * any modifications. If either fails, nothing has been changed.</p>
     *
     * <p><b>Sequence Diagram → Code mapping:</b>
     * <pre>
     * TS -> TR : findById(102)      → taskRepository.findById(taskId)
     * TS -> UR : findById(42)       → userRepository.findById(userId)
     * TS -> T  : setAssignedTo(#42) → task.setAssignedTo(assignee)
     * TS -> AS : logEvent(ASSIGNED) → auditService.logEvent(...)
     * opt [notifications enabled]   → if (assignee.isNotificationEnabled())
     * TS -> NS : sendNotification   → notificationService.sendNotification(task)
     * TS -> TR : save               → taskRepository.save(task)
     * </pre></p>
     *
     * @param taskId the task to assign
     * @param userId the user to assign it to
     * @throws EntityNotFoundException if task or user doesn't exist
     */
    public void assignTaskToUser(Long taskId, Long userId) {
        // --- Step 1: Find the task ---
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException(TASK_NOT_FOUND + taskId));

        // --- Step 2: Find the assignee ---
        // If the user doesn't exist, throw immediately — the task state is unchanged.
        User assignee = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // --- Step 3: Execute domain logic ---
        task.setAssignedTo(assignee);

        // --- Step 4: Audit the assignment ---
        auditService.logEvent(AuditAction.ASSIGNED, assignee, "Task", taskId,
                "Task assigned to user " + assignee.getUsername());

        // --- Step 5: Conditional notification ---
        // Only notify if the assignee has opted in.
        if (assignee.isNotificationEnabled()) {
            notificationService.sendNotification(task);
        }

        // --- Step 6: Persist ---
        taskRepository.save(task);
        log.info("Task assigned: taskId={} to userId={}", taskId, userId);
    }

    // =========================================================================
    // FILTERED QUERIES (Pagination + Status Filter)
    // =========================================================================

    /**
     * User's tasks with optional status filter.
     *
     * <p>The service method decides which repository query to call based on
     * whether a filter is provided. This logic belongs in the SERVICE layer,
     * not the controller — it's a business decision, not an HTTP concern.</p>
     *
     * @param user     the authenticated user
     * @param complete null = all tasks, true = completed only, false = pending only
     * @param pageable pagination parameters
     * @return paginated, optionally filtered tasks
     */
    @Transactional(readOnly = true)
    public Page<Task> findAllTasksByUserAndStatus(User user, Boolean complete, Pageable pageable) {
        if (complete == null) {
            return taskRepository.findAllByOwner(user, pageable);       // No filter
        }
        return taskRepository.findAllByOwnerAndIsComplete(user, complete, pageable);  // Filtered
    }

    /**
     * All tasks (admin) with optional status filter.
     *
     * @param complete null = all, true = completed, false = pending
     * @param pageable pagination parameters
     * @return paginated, optionally filtered tasks
     */
    @Transactional(readOnly = true)
    public Page<Task> findAllTasks(Boolean complete, Pageable pageable) {
        if (complete == null) {
            return taskRepository.findAll(pageable);                  // No filter
        }
        return taskRepository.findAllByIsComplete(complete, pageable);  // Filtered
    }
}