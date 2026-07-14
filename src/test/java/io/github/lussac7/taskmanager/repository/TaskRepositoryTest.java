/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.repository;

import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link TaskRepository} — real H2 database, no mocks.
 *
 * <h2>What @DataJpaTest does:</h2>
 * <p>Creates a SLICED Spring context that includes ONLY JPA-related beans:
 * <ul>
 *   <li>Scans for @Entity classes (User, Task, Notification, etc.).</li>
 *   <li>Sets up Spring Data JPA repositories (the real implementations, not mocks).</li>
 *   <li>Auto-configures an in-memory H2 database.</li>
 *   <li>Provides TestEntityManager for direct JPA operations.</li>
 *   <li>Does NOT load controllers, services, or Spring Security.</li>
 * </ul>
 * This makes tests extremely fast (~100ms each) while still testing real database operations.</p>
 *
 * <h2>Why test repositories separately?</h2>
 * <p>Repository tests verify that Spring Data JPA query derivation works correctly —
 * that method names like findAllByOwner generate the expected SQL. If these tests
 * pass, you know your custom queries are correct without running the full application.</p>
 *
 * @see TaskRepository
 * @see Task
 */
@DataJpaTest
@DisplayName("TaskRepository Integration Tests")
class TaskRepositoryTest {

    // =========================================================================
    // TEST DEPENDENCIES
    // =========================================================================

    /**
     * TestEntityManager is a thin wrapper around JPA's EntityManager designed
     * for testing. Key operations:
     * <ul>
     *   <li>persist() — inserts immediately (unlike repository.save() which may flush lazily)</li>
     *   <li>flush() — forces SQL to execute NOW</li>
     *   <li>clear() — detaches all entities, forcing a fresh read from the database</li>
     * </ul>
     */
    @Autowired
    private TestEntityManager entityManager;

    /**
     * The REAL repository — not a mock. Spring Data JPA generates the
     * implementation at runtime.
     */
    @Autowired
    private TaskRepository taskRepository;

    // =========================================================================
    // TEST FIXTURES
    // =========================================================================

    /** A user who owns tasks in these tests. */
    private User owner;

    /** A user who can be assigned tasks. */
    private User assignee;

    /**
     * Creates and persists two test users before each test.
     * These users get real database-assigned IDs.
     */
    @BeforeEach
    void setUp() {
        owner = new User("john", "john@example.com", "password", UserRole.USER);
        assignee = new User("jane", "jane@example.com", "password", UserRole.USER);
        // persist() inserts immediately (unlike repository.save())
        entityManager.persist(owner);
        entityManager.persist(assignee);
    }

    // =========================================================================
    // FIND OPERATIONS
    // =========================================================================
    // Tests for SELECT queries: findById, findAllByOwner, findAllByAssignedTo.

    @Nested
    @DisplayName("Find Operations")
    class FindOperations {

        /**
         * Finding a task by its ID should return it with all fields populated.
         *
         * <p><b>Why flush() and clear()?</b>
         * <ul>
         *   <li>flush() forces the INSERT to execute immediately.</li>
         *   <li>clear() detaches ALL entities from the persistence context.</li>
         *   <li>The subsequent findById() then reads FRESH from the database,
         *       proving the data was actually persisted.</li>
         * </ul>
         * Without flush()+clear(), findById() might return the in-memory object
         * without actually querying the database.</p>
         */
        @Test
        @DisplayName("Should find task by ID")
        void shouldFindTaskById() {
            // GIVEN: a task persisted to the database
            Task task = new Task("Test Task", "Description", owner);
            Long id = entityManager.persist(task).getId();
            entityManager.flush();   // Force SQL INSERT to execute now
            entityManager.clear();   // Clear the persistence context

            // WHEN: finding by ID
            Optional<Task> found = taskRepository.findById(id);

            // THEN: the task should be found with correct data
            assertThat(found).isPresent();  // Optional is not empty
            assertThat(found.get().getTitle()).isEqualTo("Test Task");
            assertThat(found.get().getOwner().getUsername()).isEqualTo("john");
        }

        /**
         * Finding a non-existent ID should return Optional.empty(), NEVER null.
         * This is a JpaRepository guarantee — all finder methods return Optional.
         */
        @Test
        @DisplayName("Should return empty Optional when task not found")
        void shouldReturnEmptyWhenNotFound() {
            assertThat(taskRepository.findById(999L)).isEmpty();
        }

        /**
         * findAllByOwner should return ALL tasks belonging to a user.
         * Spring Data JPA derives the SQL from the method name:
         * findAllByOwner → SELECT * FROM tasks WHERE owner_id = ?
         */
        @Test
        @DisplayName("Should find all tasks by owner")
        void shouldFindAllTasksByOwner() {
            // GIVEN: two tasks owned by the same user
            entityManager.persist(new Task("Task 1", "First", owner));
            entityManager.persist(new Task("Task 2", "Second", owner));
            entityManager.flush();
            entityManager.clear();

            // WHEN: finding by owner
            List<Task> tasks = taskRepository.findAllByOwner(owner);

            // THEN: both tasks should be returned, all owned by the correct user
            assertThat(tasks).hasSize(2)
                    .allMatch(t -> t.getOwner().equals(owner));
        }

        /**
         * findAllByAssignedTo should return tasks assigned to a specific user.
         *
         * <p><b>Pageable.unpaged()</b> means "don't paginate — return all results."
         * The repository method requires a Pageable parameter, but our test
         * doesn't need pagination, so we use unpaged().</p>
         */
        @Test
        @DisplayName("Should find tasks by assignee")
        void shouldFindTasksByAssignee() {
            Task task = new Task("Assigned Task", "Desc", owner);
            task.setAssignedTo(assignee);
            entityManager.persist(task);
            entityManager.flush();
            entityManager.clear();

            // Pageable.unpaged() = "give me all results, no limit"
            Page<Task> page = taskRepository.findAllByAssignedTo(assignee, Pageable.unpaged());

            List<Task> tasks = page.getContent();
            assertThat(tasks).hasSize(1);
            assertThat(tasks.get(0).getAssignedTo().getUsername()).isEqualTo("jane");
        }
    }

    // =========================================================================
    // SAVE OPERATIONS
    // =========================================================================
    // JPA's save() handles both INSERT (new entities) and UPDATE (existing entities).

    @Nested
    @DisplayName("Save Operations")
    class SaveOperations {

        /**
         * Saving a NEW (transient) task should generate an ID.
         * The database assigns the ID via auto-increment.
         */
        @Test
        @DisplayName("Should save new task and generate ID")
        void shouldSaveNewTaskAndGenerateId() {
            // GIVEN: a transient task (no ID yet)
            Task task = new Task("New Task", "Save me", owner);

            // WHEN: saving
            Task saved = taskRepository.save(task);

            // THEN: the returned task should have a database-assigned ID
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getTitle()).isEqualTo("New Task");
        }

        /**
         * Modifying an existing task and calling save() should UPDATE the database.
         * Hibernate's "dirty checking" detects that isComplete changed and
         * generates an UPDATE statement automatically.
         */
        @Test
        @DisplayName("Should update existing task")
        void shouldUpdateExistingTask() {
            // GIVEN: a persisted task
            Task task = entityManager.persist(new Task("Original", "Desc", owner));
            entityManager.flush();

            // WHEN: modifying and saving
            task.markComplete();         // Change the entity's state
            taskRepository.save(task);   // Persist the change
            entityManager.flush();       // Force UPDATE to execute
            entityManager.clear();       // Clear for fresh read

            // THEN: the change should be persisted
            Task updated = taskRepository.findById(task.getId()).orElseThrow();
            assertThat(updated.isComplete()).isTrue();
        }
    }

    // =========================================================================
    // QUERY DERIVATION
    // =========================================================================
    // Spring Data JPA can generate queries from method names.
    // countByOwner is not a standard JpaRepository method — Spring derives it.

    @Nested
    @DisplayName("Query Derivation")
    class QueryDerivation {

        /**
         * countByOwner should return the number of tasks owned by a user.
         * Spring Data JPA parses the method name:
         * countByOwner → SELECT COUNT(*) FROM tasks WHERE owner_id = ?
         */
        @Test
        @DisplayName("Should count tasks by owner")
        void shouldCountTasksByOwner() {
            entityManager.persist(new Task("A", "", owner));
            entityManager.persist(new Task("B", "", owner));
            entityManager.persist(new Task("C", "", owner));
            entityManager.flush();

            long count = taskRepository.countByOwner(owner);

            assertThat(count).isEqualTo(3);
        }
    }

    // =========================================================================
    // DELETE OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Delete Operations")
    class DeleteOperations {

        /**
         * Deleting a task should remove it from the database.
         * Subsequent findById() should return Optional.empty().
         */
        @Test
        @DisplayName("Should delete task")
        void shouldDeleteTask() {
            // GIVEN: a persisted task
            Task task = entityManager.persist(new Task("To Delete", "Desc", owner));
            Long id = task.getId();
            entityManager.flush();
            entityManager.clear();

            // WHEN: deleting
            taskRepository.delete(task);

            // THEN: the task should no longer exist
            assertThat(taskRepository.findById(id)).isEmpty();
        }
    }
}