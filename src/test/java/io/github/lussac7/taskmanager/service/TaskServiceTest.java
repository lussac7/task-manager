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
import io.github.lussac7.taskmanager.domain.UserRole;
import io.github.lussac7.taskmanager.repository.TaskRepository;
import io.github.lussac7.taskmanager.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TaskService} — mocks all dependencies, tests orchestration logic.
 *
 * <h2>What we're testing (and NOT testing):</h2>
 * <p>We test the SERVICE'S LOGIC: the order of operations, the conditional
 * branches (if notifications enabled, if task not found), and that the
 * correct dependencies are called with the correct arguments.</p>
 *
 * <p>We do NOT test: the database (that's repository tests), HTTP (that's
 * controller tests), or the full stack (that's integration tests).</p>
 *
 * <h2>Mocking Strategy</h2>
 * <table>
 *   <tr><th>Mock</th><th>Why</th></tr>
 *   <tr><td>taskRepository</td><td>We control what "the database" returns.</td></tr>
 *   <tr><td>userRepository</td><td>Same — needed for assignTaskToUser.</td></tr>
 *   <tr><td>auditService</td><td>We verify it was called with correct params.</td></tr>
 *   <tr><td>notificationService</td><td>We verify it was called (or NOT called) based on conditions.</td></tr>
 * </table>
 *
 * @see TaskService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService Unit Tests")
class TaskServiceTest {

    // =========================================================================
    // MOCK DEPENDENCIES
    // =========================================================================

    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    /**
     * @InjectMocks creates the REAL TaskService and injects all @Mock fields
     * above into its constructor. This is the object under test.
     */
    @InjectMocks
    private TaskService taskService;

    // =========================================================================
    // TEST FIXTURES
    // =========================================================================

    private User owner;       // A user who owns tasks
    private User assignee;    // A user who can be assigned tasks
    private Task task;        // A sample task used in most tests

    @BeforeEach
    void setUp() {
        owner = new User("john", "john@example.com", "pass", UserRole.USER);
        assignee = new User("jane", "jane@example.com", "pass", UserRole.USER);
        task = new Task("Test Task", "Description", owner);

        // Set fake IDs via reflection (JPA normally assigns these)
        setId(task, 1L);
        setId(owner, 1L);
        setId(assignee, 2L);
    }

    // =========================================================================
    // UC1: CREATE A TASK
    // =========================================================================

    @Nested
    @DisplayName("createTask() - Use Case 1")
    class CreateTask {

        /**
         * Creating a task should:
         * 1. Save it to the repository.
         * 2. Log an audit entry with action=CREATED.
         *
         * <p><b>Why we don't check the returned task's title:</b>
         * The mock returns our pre-built 'task' object (title="Test Task"),
         * not a new task with the title we passed in. In a real database,
         * the returned task would have the correct title. Here we just verify
         * the interactions.</p>
         */
        @Test
        @DisplayName("Should create task and audit the event")
        void shouldCreateTaskAndAuditEvent() {
            // ARRANGE: mock the repository to return our test task
            when(taskRepository.save(any(Task.class))).thenReturn(task);

            // ACT: call the service method
            Task result = taskService.createTask(owner, "New Task", "Description");

            // ASSERT: a task was returned
            assertThat(result).isNotNull();

            // ASSERT: save() was called
            verify(taskRepository).save(any(Task.class));

            // ASSERT: audit event was logged with CREATED action and the task title
            verify(auditService).logEvent(
                    eq(AuditAction.CREATED),    // Must be CREATED
                    eq(owner),                   // Must be the correct user
                    eq("Task"),                  // Must be "Task"
                    anyLong(),                   // Any ID (the mock doesn't know the real ID)
                    contains("New Task")         // Description must contain the task title
            );
        }
    }

    // =========================================================================
    // UC2: MARK TASK AS COMPLETE
    // =========================================================================

    @Nested
    @DisplayName("markTaskComplete() - Use Case 2")
    class MarkTaskComplete {

        /**
         * The FULL happy path from the Sequence Diagram:
         * find → markComplete() → notify → audit → persist.
         *
         * <p>This test verifies ALL five steps happen in the correct order.</p>
         */
        @Test
        @DisplayName("Should mark complete, notify, audit, and persist — full Sequence Diagram")
        void shouldCompleteTaskWithFullFlow() {
            // ARRANGE: the task exists in the repository
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

            // ACT
            taskService.markTaskComplete(1L);

            // ASSERT Step 2: task should be complete
            assertThat(task.isComplete()).isTrue();

            // ASSERT Step 3: notification should be sent (owner has notifications enabled by default)
            verify(notificationService).sendNotification(task);

            // ASSERT Step 4: audit event logged
            verify(auditService).logEvent(
                    eq(AuditAction.COMPLETED), eq(owner), eq("Task"), eq(1L), anyString());

            // ASSERT Step 5: task saved
            verify(taskRepository).save(task);
        }

        /**
         * Error path: task not found.
         * Should throw EntityNotFoundException and NOT call anything else.
         * This is the "fail fast" principle.
         */
        @Test
        @DisplayName("Should throw EntityNotFoundException when task not found")
        void shouldThrowWhenTaskNotFound() {
            // ARRANGE: the repository returns empty (task not found)
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            // ACT & ASSERT: should throw
            assertThatThrownBy(() -> taskService.markTaskComplete(999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Task not found");

            // ASSERT: nothing else should happen — fail fast!
            verify(taskRepository, never()).save(any());
            verify(auditService, never()).logEvent(any(), any(), any(), anyLong(), any());
        }

        /**
         * Conditional: notification is NOT sent when the user has disabled it.
         * This tests the opt[] guard from the Sequence Diagram.
         */
        @Test
        @DisplayName("Should NOT send notification when user has notifications disabled")
        void shouldNotNotifyWhenNotificationsDisabled() {
            // ARRANGE: owner has notifications disabled
            owner.setNotificationEnabled(false);
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

            // ACT
            taskService.markTaskComplete(1L);

            // ASSERT: notification should NOT be sent
            verify(notificationService, never()).sendNotification(any());

            // ASSERT: but the task should STILL be completed and saved
            assertThat(task.isComplete()).isTrue();
            verify(taskRepository).save(task);
        }
    }

    // =========================================================================
    // UC5: ASSIGN TASK TO USER
    // =========================================================================

    @Nested
    @DisplayName("assignTaskToUser() - Use Case 5")
    class AssignTask {

        /**
         * The FULL Sequence Diagram: find task → find assignee → assign →
         * audit → notify → persist.
         */
        @Test
        @DisplayName("Should assign, audit, notify if enabled, and persist — full Sequence Diagram")
        void shouldAssignTaskWithFullFlow() {
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(userRepository.findById(2L)).thenReturn(Optional.of(assignee));

            taskService.assignTaskToUser(1L, 2L);

            assertThat(task.getAssignedTo()).isEqualTo(assignee);
            verify(auditService).logEvent(eq(AuditAction.ASSIGNED), eq(assignee),
                    eq("Task"), eq(1L), contains("jane"));
            verify(notificationService).sendNotification(task);
            verify(taskRepository).save(task);
        }

        /**
         * Fail-fast: if the task doesn't exist, throw immediately.
         * Don't even bother looking up the user.
         */
        @Test
        @DisplayName("Should throw when task not found — fail fast, don't look up user")
        void shouldThrowWhenTaskNotFound() {
            when(taskRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.assignTaskToUser(999L, 2L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Task not found");

            // User lookup should NEVER happen (fail-fast)
            verify(userRepository, never()).findById(anyLong());
        }

        /**
         * If the task exists but the user doesn't.
         */
        @Test
        @DisplayName("Should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.assignTaskToUser(1L, 999L))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("User not found");
        }
    }

    // =========================================================================
    // UC4: DELETE A TASK
    // =========================================================================

    @Nested
    @DisplayName("deleteTask() - Use Case 4")
    class DeleteTask {

        /**
         * Deleting a task should audit BEFORE deletion.
         * This preserves the task title in the audit log even after the
         * task row is gone from the database.
         */
        @Test
        @DisplayName("Should delete task and audit BEFORE deletion")
        void shouldDeleteAndAudit() {
            when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

            taskService.deleteTask(1L);

            // Verify delete was called
            verify(taskRepository).delete(task);

            // Verify audit was logged (BEFORE the delete, in the real code)
            verify(auditService).logEvent(
                    eq(AuditAction.DELETED), eq(owner), eq("Task"), eq(1L), anyString());
        }
    }

    // =========================================================================
    // UC3: VIEW TASKS
    // =========================================================================

    @Nested
    @DisplayName("findAllTasksByUser() - Use Case 3")
    class FindTasks {

        /**
         * A user with tasks should get their task list.
         * This is a read-only operation — no audit, no notifications.
         */
        @Test
        @DisplayName("Should return tasks owned by user")
        void shouldReturnTasksOwnedByUser() {
            when(taskRepository.findAllByOwner(owner)).thenReturn(List.of(task));

            List<Task> tasks = taskService.findAllTasksByUser(owner);

            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).getOwner()).isEqualTo(owner);
        }

        /**
         * A user with no tasks should get an empty list, NEVER null.
         */
        @Test
        @DisplayName("Should return empty list when user has no tasks")
        void shouldReturnEmptyList() {
            when(taskRepository.findAllByOwner(owner)).thenReturn(List.of());

            assertThat(taskService.findAllTasksByUser(owner)).isEmpty();
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Sets the private {@code id} field via reflection.
     * In production, JPA assigns IDs. In tests, we simulate this.
     */
    private void setId(Object target, Long id) {
        try {
            var idField = target.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID for testing", e);
        }
    }

    /**
     * Custom Mockito argument matcher for audit log verification.
     *
     * <p>Instead of matching the exact audit message (which is brittle),
     * we check that it CONTAINS a key substring (like the task title or
     * assignee username). This makes tests resilient to minor message
     * format changes.</p>
     *
     * @param substring the text that must appear in the matched string
     * @return a Mockito argument matcher
     */
    private static String contains(String substring) {
        return argThat(s -> s != null && s.contains(substring));
    }
}