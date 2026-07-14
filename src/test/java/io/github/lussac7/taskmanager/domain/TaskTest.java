/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Task} domain logic — no Spring context needed.
 *
 * <p>These are <b>pure unit tests</b>. They test the Task class in complete
 * isolation — no Spring, no database, no mocks. The Task class doesn't depend
 * on any framework, so these tests run in milliseconds.</p>
 *
 * <p><b>What we verify:</b></p>
 * <ul>
 *   <li>New tasks have the correct initial state.</li>
 *   <li>markComplete() transitions false → true, but not true → true.</li>
 *   <li>updateTitle() rejects null and blank strings.</li>
 *   <li>setAssignedTo() allows assignment and reassignment.</li>
 *   <li>equals() is based on database ID (JPA best practice).</li>
 * </ul>
 *
 * @see Task
 */
@DisplayName("Task Entity Unit Tests")
class TaskTest {

    // =========================================================================
    // TEST FIXTURES
    // =========================================================================
    // These objects are created fresh before EACH test method.
    // This ensures tests are isolated — if one test modifies an object,
    // it doesn't affect the next test.

    /** A user who will own tasks in these tests. */
    private User owner;

    /** A user who tasks can be assigned to. */
    private User assignee;

    @BeforeEach
    void setUp() {
        // Create fresh test users before each test.
        // The password is a placeholder — we're testing Task logic, not User logic.
        owner = new User("john", "john@example.com", "encodedPass", UserRole.USER);
        assignee = new User("jane", "jane@example.com", "encodedPass", UserRole.USER);
    }

    // =========================================================================
    // CONSTRUCTION TESTS
    // =========================================================================
    // Verify that a newly created Task has the correct initial state.
    // This is important because the Task constructor sets defaults that
    // the rest of the application depends on.

    @Nested
    @DisplayName("Task Construction")
    class Construction {

        /**
         * A newly created task should have all provided fields set correctly,
         * and default values for fields not provided (isComplete=false,
         * assignedTo=null, createdAt=now).
         */
        @Test
        @DisplayName("Should create task with required fields")
        void shouldCreateTaskWithRequiredFields() {
            // WHEN: a new task is created
            Task task = new Task("Test Task", "Description", owner);

            // THEN: all provided fields should match
            assertThat(task.getTitle()).isEqualTo("Test Task");
            assertThat(task.getDescription()).isEqualTo("Description");
            assertThat(task.getOwner()).isEqualTo(owner);

            // AND: default values should be set correctly
            assertThat(task.isComplete()).isFalse();        // New tasks are NOT done yet
            assertThat(task.getAssignedTo()).isNull();      // New tasks have no assignee
            assertThat(task.getCreatedAt()).isNotNull();    // Timestamp is auto-generated
        }

        /**
         * The description is optional — a task can be created without one.
         * This is useful for quick tasks where you only need a title.
         */
        @Test
        @DisplayName("Should create task without description")
        void shouldCreateTaskWithoutDescription() {
            Task task = new Task("Title Only", null, owner);

            assertThat(task.getTitle()).isEqualTo("Title Only");
            assertThat(task.getDescription()).isNull();     // Description can be null
        }
    }

    // =========================================================================
    // MARK COMPLETE TESTS
    // =========================================================================
    // The most important business rule in the system:
    // A task can be completed ONCE, and only ONCE.
    // There is no "un-complete" operation.

    @Nested
    @DisplayName("markComplete() — one-way transition, cannot be undone")
    class MarkComplete {

        /**
         * An incomplete task should become complete after calling markComplete().
         *
         * <p>This is the "happy path" — the normal flow when a user clicks
         * the "Complete" button on a pending task.</p>
         */
        @Test
        @DisplayName("Should mark incomplete task as complete")
        void shouldMarkIncompleteTaskAsComplete() {
            // GIVEN: a task that is NOT yet complete
            Task task = new Task("Test", "Desc", owner);
            assertThat(task.isComplete()).isFalse();    // Verify initial state

            // WHEN: markComplete() is called
            task.markComplete();

            // THEN: the task should now be complete
            assertThat(task.isComplete()).isTrue();
        }

        /**
         * Calling markComplete() on an already-completed task should throw
         * an exception. This enforces the one-way transition rule.
         *
         * <p><b>Why is this important?</b> Without this guard, someone could
         * accidentally "complete" a task that was already done, triggering
         * notifications and audit entries for an event that didn't happen.</p>
         *
         * <p><b>assertThatThrownBy</b> is an AssertJ method that verifies
         * a piece of code throws an exception. It's cleaner than try-catch
         * blocks in tests.</p>
         */
        @Test
        @DisplayName("Should throw when marking already-completed task")
        void shouldThrowWhenMarkingAlreadyCompletedTask() {
            // GIVEN: a task that IS already complete
            Task task = new Task("Test", "Desc", owner);
            task.markComplete();  // First completion succeeds

            // WHEN/THEN: calling markComplete() again should throw
            assertThatThrownBy(task::markComplete)                    // The action we expect to throw
                    .isInstanceOf(IllegalStateException.class)        // The type of exception
                    .hasMessageContaining("already completed");       // The error message
        }
    }

    // =========================================================================
    // UPDATE TITLE TESTS
    // =========================================================================
    // The title is the only required field for a task.
    // These tests verify the validation logic that prevents invalid titles.

    @Nested
    @DisplayName("updateTitle() — title cannot be null or blank")
    class UpdateTitle {

        /**
         * Updating a title with valid text should work.
         */
        @Test
        @DisplayName("Should update title with valid input")
        void shouldUpdateTitleWithValidInput() {
            Task task = new Task("Old Title", "Desc", owner);

            task.updateTitle("New Title");

            assertThat(task.getTitle()).isEqualTo("New Title");
        }

        /**
         * Null is not a valid title. The method should throw immediately.
         */
        @Test
        @DisplayName("Should throw when title is null")
        void shouldThrowWhenTitleIsNull() {
            Task task = new Task("Title", "Desc", owner);

            // We pass a lambda () -> task.updateTitle(null) because
            // assertThatThrownBy needs a piece of code to execute.
            assertThatThrownBy(() -> task.updateTitle(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Title cannot be blank");
        }

        /**
         * A string containing only whitespace (spaces, tabs) is also invalid.
         * String.isBlank() returns true for "", "   ", "\t", etc.
         */
        @Test
        @DisplayName("Should throw when title is blank")
        void shouldThrowWhenTitleIsBlank() {
            Task task = new Task("Title", "Desc", owner);

            assertThatThrownBy(() -> task.updateTitle("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Title cannot be blank");
        }
    }

    // =========================================================================
    // ASSIGN TASK TESTS
    // =========================================================================
    // Unlike markComplete(), assignment has no business restrictions.
    // A task can be assigned, reassigned, or unassigned freely.

    @Nested
    @DisplayName("setAssignedTo() — no restrictions, can reassign freely")
    class AssignTask {

        /**
         * An unassigned task should become assigned.
         */
        @Test
        @DisplayName("Should assign task to a user")
        void shouldAssignTaskToUser() {
            Task task = new Task("Test", "Desc", owner);
            assertThat(task.getAssignedTo()).isNull();  // Initially unassigned

            task.setAssignedTo(assignee);

            assertThat(task.getAssignedTo()).isEqualTo(assignee);
        }

        /**
         * A task can be reassigned from one user to another.
         * There's no restriction — the old assignee is simply replaced.
         */
        @Test
        @DisplayName("Should reassign task to different user")
        void shouldReassignTaskToDifferentUser() {
            Task task = new Task("Test", "Desc", owner);
            task.setAssignedTo(assignee);  // First assignment
            User anotherUser = new User("bob", "bob@example.com", "pass", UserRole.USER);

            task.setAssignedTo(anotherUser);  // Reassign to someone else

            assertThat(task.getAssignedTo()).isEqualTo(anotherUser);
        }
    }

    // =========================================================================
    // EQUALS & HASHCODE TESTS
    // =========================================================================
    // JPA best practice: two entities are equal if they have the same ID.
    // This is different from regular Java objects (where equality is usually
    // based on all fields). The reason: two Java objects representing the
    // same database row should be considered equal, even if they were loaded
    // in different transactions.

    @Nested
    @DisplayName("equals/hashCode — based on ID (JPA best practice)")
    class Equality {

        /**
         * Two brand-new (unpersisted) tasks should NOT be equal, even if
         * they have identical fields. Both have null IDs, so they represent
         * different objects that haven't been saved yet.
         */
        @Test
        @DisplayName("Tasks with null ids should not be equal")
        void tasksWithNullIdsShouldNotBeEqual() {
            // Two new tasks with identical fields but no IDs (not yet persisted)
            Task task1 = new Task("A", "Desc", owner);
            Task task2 = new Task("A", "Desc", owner);

            // They should NOT be equal — both have null IDs
            assertThat(task1).isNotEqualTo(task2);
        }

        /**
         * Two tasks with the same ID represent the same database row.
         * They should be equal, even if other fields differ.
         * This simulates loading the same row into two different Java objects
         * (e.g., in two different HTTP requests).
         */
        @Test
        @DisplayName("Equal id should mean equal objects")
        void equalIdShouldMeanEqualObjects() {
            Task task1 = createTaskWithId(1L, owner);  // Task with ID=1
            Task task2 = createTaskWithId(1L, owner);  // Another task with ID=1

            // They should be equal because they have the same ID
            assertThat(task1).isEqualTo(task2);
        }

        /**
         * Helper method that creates a Task with a specific ID for testing equality.
         *
         * <p>Uses reflection because the 'id' field has no public setter.
         * In production, JPA sets the ID when the entity is persisted.
         * In tests, we simulate this with reflection.</p>
         *
         * @param id    the fake database ID to assign
         * @param owner the task owner
         * @return a Task with the specified ID
         */
        private Task createTaskWithId(Long id, User owner) {
            Task task = new Task("Title", "Desc", owner);
            try {
                // Use reflection to set the private 'id' field
                var idField = Task.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(task, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return task;
        }
    }
}