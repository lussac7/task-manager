/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lussac7.taskmanager.domain.AuditAction;
import io.github.lussac7.taskmanager.domain.AuditEntry;
import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.domain.UserRole;
import io.github.lussac7.taskmanager.repository.AuditRepository;
import io.github.lussac7.taskmanager.repository.TaskRepository;
import io.github.lussac7.taskmanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Task Manager REST API.
 *
 * <h2>What makes these "integration" tests?</h2>
 * <p>Unlike unit tests which mock dependencies, these tests use the <b>real</b>
 * Spring ApplicationContext, a real embedded H2 database, and real
 * service/repository beans. Each test sends an HTTP request through the
 * FULL Spring MVC stack — filters, controllers, services, repositories —
 * and verifies BOTH the response AND the database state.</p>
 *
 * <h2>Key Annotations</h2>
 * <ul>
 *   <li><b>@SpringBootTest:</b> Loads the ENTIRE application context.
 *       This is slow (~5 seconds) but tests the real thing.</li>
 *   <li><b>@AutoConfigureMockMvc:</b> Provides MockMvc for sending fake HTTP requests.</li>
 *   <li><b>@Transactional:</b> Rolls back the database after each test.
 *       Each test starts with a clean slate.</li>
 *   <li><b>@WithMockUser:</b> Simulates an authenticated user for Spring Security.</li>
 * </ul>
 *
 * <h2>What we test (end-to-end):</h2>
 * <ol>
 *   <li>HTTP request → Controller → Service → Repository → Database</li>
 *   <li>Response status and JSON body</li>
 *   <li>Database state (was the data actually persisted?)</li>
 *   <li>Security (does the role check work?)</li>
 * </ol>
 *
 * @see io.github.lussac7.taskmanager.controller.TaskController
 * @see io.github.lussac7.taskmanager.controller.AdminController
 * @see io.github.lussac7.taskmanager.controller.UserController
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Task API Integration Tests")
class TaskApiIntegrationTest {

    // =========================================================================
    // REAL DEPENDENCIES (Not mocked!)
    // =========================================================================
    // In integration tests, we use the REAL beans, not mocks.
    // The only exception is external services (email, payment gateways)
    // which we don't have in this project.

    @Autowired private MockMvc mockMvc;           // Fake HTTP client
    @Autowired private ObjectMapper objectMapper;  // JSON ↔ Java converter
    @Autowired private UserRepository userRepository;     // Real database access
    @Autowired private TaskRepository taskRepository;     // Real database access
    @Autowired private AuditRepository auditRepository;   // Real database access
    @Autowired private PasswordEncoder passwordEncoder;   // Real BCrypt encoder

    // =========================================================================
    // SETUP: Create test users in the database
    // =========================================================================
    // These users are created BEFORE each test and rolled back AFTER.
    // The existsByUsername check makes this idempotent — if users already
    // exist (from a previous test in the same transaction), skip creation.

    @BeforeEach
    void setUp() {
        // Alice: primary test user, notifications enabled
        if (!userRepository.existsByUsername("alice")) {
            User alice = new User("alice", "alice@test.com",
                    passwordEncoder.encode("password123"), UserRole.USER);
            alice.setNotificationEnabled(true);
            userRepository.save(alice);
        }
        // Bob: secondary test user, notifications disabled (for testing opt[] guard)
        if (!userRepository.existsByUsername("bob")) {
            User bob = new User("bob", "bob@test.com",
                    passwordEncoder.encode("password123"), UserRole.USER);
            bob.setNotificationEnabled(false);
            userRepository.save(bob);
        }
        // Admin: has ROLE_ADMIN for testing admin-only endpoints
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User("admin", "admin@test.com",
                    passwordEncoder.encode("admin123"), UserRole.ADMIN);
            admin.setNotificationEnabled(true);
            userRepository.save(admin);
        }
    }

    // =========================================================================
    // UC1: CREATE A TASK
    // =========================================================================

    @Nested
    @DisplayName("POST /api/tasks - Create Task")
    class CreateTask {

        /**
         * Happy path: an authenticated user creates a valid task.
         *
         * <p>We verify THREE things:
         * <ol>
         *   <li>The HTTP response is 201 Created with the correct JSON.</li>
         *   <li>The task is ACTUALLY in the database (not just a mock response).</li>
         *   <li>An audit entry was recorded for the creation.</li>
         * </ol></p>
         */
        @Test
        @WithMockUser(username = "alice", roles = {"USER"})
        @DisplayName("Should create task and return 201 with task data")
        void shouldCreateTask() throws Exception {
            // --- Arrange: Prepare the JSON request body ---
            String json = """
                {"title":"API Test Task","description":"Created via integration test"}
                """;

            // --- Act: Send the HTTP request ---
            mockMvc.perform(post("/api/tasks")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    // --- Assert: Check the HTTP response ---
                    .andExpect(status().isCreated())                       // 201 Created
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.title").value("API Test Task"))
                    .andExpect(jsonPath("$.data.ownerUsername").value("alice"))
                    .andExpect(jsonPath("$.data.complete").value(false)); // New tasks are incomplete

            // --- Assert: Verify the database state ---
            // Load Alice from the database
            User alice = userRepository.findByUsername("alice").orElseThrow();
            // Check that her tasks include the one we just created
            assertThat(taskRepository.findAllByOwner(alice))
                    .anyMatch(t -> t.getTitle().equals("API Test Task"));

            // --- Assert: Verify the audit log ---
            // An audit entry with action=CREATED should exist for our task
            assertThat(auditRepository.findAllByAction(AuditAction.CREATED))
                    .anyMatch(e -> e.getDetails().contains("API Test Task"));
        }

        /**
         * Validation: a task with a blank title should return 400 Bad Request.
         * The @NotBlank annotation on CreateTaskRequest.title triggers this.
         */
        @Test
        @WithMockUser(username = "alice", roles = {"USER"})
        @DisplayName("Should return 400 when title is blank")
        void shouldReturn400ForBlankTitle() throws Exception {
            mockMvc.perform(post("/api/tasks")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"\",\"description\":\"Desc\"}"))
                    .andExpect(status().isBadRequest());
        }

        /**
         * Authentication: an unauthenticated request should return 401.
         * No @WithMockUser annotation = anonymous user.
         */
        @Test
        @DisplayName("Should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/api/tasks")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Test\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // UC2 + UC3: MARK COMPLETE + VIEW TASKS
    // =========================================================================

    @Nested
    @DisplayName("Mark Complete + View Tasks")
    class CompleteAndView {

        /**
         * Full workflow: create a task → mark it complete → verify in database
         * → verify via API list → verify audit log.
         *
         * <p>This tests the entire "Mark Task as Complete" Sequence Diagram
         * from end to end.</p>
         */
        @Test
        @WithMockUser(username = "alice", roles = {"USER"})
        @DisplayName("Should mark task complete and verify via paginated list")
        void shouldCompleteAndVerify() throws Exception {
            // --- Step 1: Create a task ---
            User alice = userRepository.findByUsername("alice").orElseThrow();
            Task task = taskRepository.save(new Task("To Complete", "Finish me", alice));

            // --- Step 2: Mark it complete via API ---
            mockMvc.perform(patch("/api/tasks/" + task.getId() + "/complete")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Task marked as complete"));

            // --- Step 3: Verify in database ---
            assertThat(taskRepository.findById(task.getId()).orElseThrow().isComplete())
                    .isTrue();

            // --- Step 4: Verify via paginated API list ---
            // The JSON path filter [?(@.title == 'To Complete')] finds the element
            // in the array where the title matches, then checks its .complete field.
            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.title == 'To Complete')].complete")
                            .value(true));

            // --- Step 5: Verify audit log ---
            List<AuditEntry> entries = auditRepository
                    .findAllByTargetTypeAndTargetId("Task", task.getId());
            assertThat(entries)
                    .extracting(AuditEntry::getAction)     // Get the action from each entry
                    .contains(AuditAction.COMPLETED);       // Should include COMPLETED
        }
    }

    // =========================================================================
    // UC4: ADMIN DELETE
    // =========================================================================

    @Nested
    @DisplayName("DELETE /api/admin/tasks/{id}")
    class AdminDelete {

        /**
         * Admin deletes a task — should succeed.
         */
        @Test
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        @DisplayName("Admin should delete task")
        void adminShouldDelete() throws Exception {
            User alice = userRepository.findByUsername("alice").orElseThrow();
            Task task = taskRepository.save(new Task("To Delete", "Remove", alice));

            mockMvc.perform(delete("/api/admin/tasks/" + task.getId())
                            .with(csrf()))
                    .andExpect(status().isOk());

            // Verify the task is GONE from the database
            assertThat(taskRepository.findById(task.getId())).isEmpty();
        }

        /**
         * Regular user tries to delete — should get 403 Forbidden.
         * This tests Spring Security's authorization rules.
         */
        @Test
        @WithMockUser(username = "alice", roles = {"USER"})
        @DisplayName("Regular user should receive 403")
        void regularUserShouldGet403() throws Exception {
            User alice = userRepository.findByUsername("alice").orElseThrow();
            Task task = taskRepository.save(new Task("Protected", "No", alice));

            mockMvc.perform(delete("/api/admin/tasks/" + task.getId())
                            .with(csrf()))
                    .andExpect(status().isForbidden());  // 403, not 401

            // Verify the task STILL EXISTS (nothing was changed)
            assertThat(taskRepository.findById(task.getId())).isPresent();
        }
    }

    // =========================================================================
    // UC5: ASSIGN TASK
    // =========================================================================

    @Nested
    @DisplayName("PATCH /api/tasks/assign")
    class AssignTask {

        /**
         * Assign a task from one user to another.
         * Verifies the assignment is persisted in the database.
         */
        @Test
        @WithMockUser(username = "alice", roles = {"USER"})
        @DisplayName("Should assign task to another user")
        void shouldAssignTask() throws Exception {
            User alice = userRepository.findByUsername("alice").orElseThrow();
            User bob = userRepository.findByUsername("bob").orElseThrow();
            Task task = taskRepository.save(new Task("Assign me", "Please", alice));

            // Build the JSON body dynamically with the actual IDs
            String body = String.format("{\"taskId\":%d,\"userId\":%d}", task.getId(), bob.getId());

            mockMvc.perform(patch("/api/tasks/assign")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            // Verify the assignee is set in the database
            assertThat(taskRepository.findById(task.getId()).orElseThrow()
                    .getAssignedTo().getUsername()).isEqualTo("bob");
        }
    }

    // =========================================================================
    // PAGINATION & FILTERING
    // =========================================================================

    @Nested
    @DisplayName("GET /api/tasks — Pagination & Filtering")
    class Pagination {

        /**
         * Tests that pagination and the ?complete= filter work correctly.
         *
         * <p>Creates 3 new tasks (1 completed, 2 pending), then verifies:
         * <ul>
         *   <li>Page 0 with size 2 returns exactly 2 tasks.</li>
         *   <li>totalElements matches the expected count.</li>
         *   <li>Filtering by ?complete=true returns only completed tasks.</li>
         * </ul></p>
         */
        @Test
        @WithMockUser(username = "alice", roles = {"USER"})
        @DisplayName("Should paginate and filter tasks")
        void shouldPaginateAndFilter() throws Exception {
            User alice = userRepository.findByUsername("alice").orElseThrow();

            // Count tasks that already exist (from DataInitializer or other tests)
            long existingTasks = taskRepository.findAllByOwner(alice).size();

            // Create 3 new tasks. Task 1 is completed, Tasks 2 and 3 are pending.
            for (int i = 1; i <= 3; i++) {
                Task t = new Task("Task " + i, "Desc " + i, alice);
                if (i == 1) t.markComplete();  // Only the first one is completed
                taskRepository.save(t);
            }

            long expectedTotal = existingTasks + 3;

            // --- Test pagination: page 0, size 2 ---
            // Should return 2 items even though there are more
            mockMvc.perform(get("/api/tasks?page=0&size=2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(2))
                    .andExpect(jsonPath("$.data.totalElements").value((int) expectedTotal));

            // --- Test filtering: only completed tasks ---
            // Should return tasks where .complete = true
            mockMvc.perform(get("/api/tasks?complete=true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].complete").value(true));
        }
    }

    // =========================================================================
    // USER MANAGEMENT
    // =========================================================================

    @Nested
    @DisplayName("User Management API")
    class UserManagement {

        /**
         * Admin can list all users. We created 3 users in setUp(), so we
         * expect exactly 3 in the response.
         */
        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("Admin should list all users")
        void adminShouldListUsers() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(3));  // alice, bob, admin
        }

        /**
         * Regular user cannot access the user list.
         * Spring Security returns 403 before the controller is even called.
         */
        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("Regular user should not access user list")
        void regularUserShouldGet403() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isForbidden());
        }

        /**
         * Public registration endpoint — anyone can create an account.
         * No @WithMockUser needed because this endpoint is permitAll().
         */
        @Test
        @DisplayName("Should register a new user")
        void shouldRegisterUser() throws Exception {
            String json = """
                {"username":"charlie","email":"charlie@test.com","password":"charlie123"}
                """;

            mockMvc.perform(post("/api/users/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));

            // Verify the user is ACTUALLY in the database
            assertThat(userRepository.existsByUsername("charlie")).isTrue();
        }
    }
}