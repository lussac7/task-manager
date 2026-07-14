/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.controller;

import io.github.lussac7.taskmanager.config.CurrentUserArgumentResolver;
import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.domain.UserRole;
import io.github.lussac7.taskmanager.service.TaskService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link AdminController}.
 *
 * <p>Tests the HTTP layer in isolation: the real controller is tested, but
 * its dependency (TaskService) is replaced by a mock. No database, no real
 * business logic — just HTTP request → response verification.</p>
 *
 * <p><b>What @WebMvcTest does:</b> Loads ONLY the controller, Spring Security
 * filters, and Jackson. Everything else must be mocked with @MockitoBean.</p>
 *
 * <p><b>@WithMockUser:</b> Simulates an authenticated user. The roles attribute
 * tells Spring Security what permissions the fake user has.</p>
 *
 * @see AdminController
 * @see TaskService
 */
@WebMvcTest(AdminController.class)
@Import(CurrentUserArgumentResolver.class)
@DisplayName("AdminController Web Layer Tests")
class AdminControllerTest {

    // =========================================================================
    // TEST DEPENDENCIES
    // =========================================================================

    /**
     * MockMvc simulates HTTP requests without starting a real web server.
     * You write code that looks like an HTTP call, and Spring executes it
     * through the full MVC stack (filters, controllers) in memory.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * @MockitoBean creates a Mockito mock AND registers it in the Spring
     * test context. When AdminController calls taskService.deleteTask(),
     * it calls THIS mock, not the real service. We control what the mock
     * returns (or throws) using when(...).thenReturn(...).
     */
    @MockitoBean
    private TaskService taskService;

    // =========================================================================
    // TEST FIXTURES
    // =========================================================================

    /** A sample task used as the return value from mocked service calls. */
    private Task testTask;

    /**
     * Runs BEFORE each @Test method. Creates fresh test data so tests
     * don't interfere with each other.
     */
    @BeforeEach
    void setUp() {
        // Create a fake admin user and a fake task.
        // IDs are set via reflection because JPA normally assigns them.
        User admin = new User("admin", "admin@example.com", "pass", UserRole.ADMIN);
        setId(admin, 99L);
        testTask = new Task("Test Task", "Description", admin);
        setId(testTask, 100L);
    }

    // =========================================================================
    // UC4: DELETE /api/admin/tasks/{id}
    // =========================================================================

    @Nested
    @DisplayName("DELETE /api/admin/tasks/{id}")
    class DeleteTaskEndpoint {

        /**
         * Happy path: admin deletes an existing task.
         *
         * <p>What we test:
         * - The endpoint returns HTTP 200.
         * - The response body contains "Task deleted".
         * - The service's deleteTask() was called with the correct ID.</p>
         */
        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("Should delete task and return 200 OK")
        void shouldDeleteTaskAndReturn200() throws Exception {
            // ARRANGE: tell the mock "when deleteTask(100L) is called, do nothing"
            doNothing().when(taskService).deleteTask(100L);

            // ACT: send a DELETE request to /api/admin/tasks/100
            mockMvc.perform(delete("/api/admin/tasks/100").with(csrf()))
                    // ASSERT: check the response
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Task deleted"));

            // ASSERT: verify the service was called exactly once with 100L
            verify(taskService).deleteTask(100L);
        }

        /**
         * Error path: task not found.
         *
         * <p>When the service throws EntityNotFoundException, the
         * GlobalExceptionHandler should convert it to HTTP 404.</p>
         */
        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("Should return 404 when task not found")
        void shouldReturn404WhenTaskNotFound() throws Exception {
            // ARRANGE: tell the mock "when deleteTask(999L) is called, throw exception"
            doThrow(new EntityNotFoundException("Task not found: 999"))
                    .when(taskService).deleteTask(999L);

            // ACT & ASSERT
            mockMvc.perform(delete("/api/admin/tasks/999").with(csrf()))
                    .andExpect(status().isNotFound());
        }

        /**
         * Auth error: no credentials provided.
         *
         * <p>Without @WithMockUser, Spring Security treats the request as
         * anonymous. For /api/** endpoints, SecurityConfig returns 401.</p>
         */
        @Test
        @DisplayName("Should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(delete("/api/admin/tasks/100").with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // UC3 (Admin Scope): GET /api/admin/tasks
    // =========================================================================

    @Nested
    @DisplayName("GET /api/admin/tasks")
    class ViewAllTasksEndpoint {

        /**
         * Admin retrieves all tasks with pagination.
         *
         * <p>isNull() is a Mockito argument matcher that matches null values.
         * Here it matches the "complete" parameter being absent (no filter).
         * any(Pageable.class) matches any Pageable object.</p>
         */
        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("Should return all tasks paginated")
        void shouldReturnAllTasks() throws Exception {
            // ARRANGE: mock returns a Page with one task
            when(taskService.findAllTasks(isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(testTask)));

            // ACT & ASSERT
            mockMvc.perform(get("/api/admin/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].title").value("Test Task"))
                    .andExpect(jsonPath("$.data.page").value(0));
        }
    }

    // =========================================================================
    // HELPER: Set ID via reflection
    // =========================================================================
    // In production, JPA assigns IDs when entities are persisted.
    // In unit tests, there's no database, so we inject a fake ID manually.
    // Reflection is the standard way to do this without adding a public setter.

    /** Sets the private {@code id} field via reflection. Used because JPA normally assigns IDs. */
    private void setId(Object target, Long id) {
        try {
            var idField = target.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set ID for testing on "
                    + target.getClass().getSimpleName(), e);
        }
    }
}