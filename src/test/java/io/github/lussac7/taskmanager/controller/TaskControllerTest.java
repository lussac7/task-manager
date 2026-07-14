/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.lussac7.taskmanager.config.CurrentUserArgumentResolver;
import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.domain.UserRole;
import io.github.lussac7.taskmanager.dto.CreateTaskRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link TaskController}.
 *
 * <p>Tests the HTTP layer in isolation. TaskService is replaced by a mock.
 * We verify that the controller:
 * <ul>
 *   <li>Returns the correct HTTP status codes (200, 201, 400, 401, 404).</li>
 *   <li>Calls the correct service methods with the correct parameters.</li>
 *   <li>Returns properly structured JSON responses (AppResponse format).</li>
 *   <li>Enforces authentication (401 when not authenticated).</li>
 * </ul></p>
 *
 * @see TaskController
 * @see TaskService
 */
@WebMvcTest(TaskController.class)
@Import(CurrentUserArgumentResolver.class)
@DisplayName("TaskController Web Layer Tests")
class TaskControllerTest {

    // =========================================================================
    // TEST DEPENDENCIES
    // =========================================================================

    /** Simulates HTTP requests without a real web server. */
    @Autowired
    private MockMvc mockMvc;

    /** Converts Java objects to JSON strings for request bodies. */
    @Autowired
    private ObjectMapper objectMapper;

    /** Replaces the real TaskService with a mock. We control what it returns. */
    @MockitoBean
    private TaskService taskService;

    // =========================================================================
    // TEST FIXTURES
    // =========================================================================

    /** A fake user representing the authenticated principal. */
    private User testUser;

    /** A fake task returned by mocked service calls. */
    private Task testTask;

    @BeforeEach
    void setUp() {
        testUser = new User("alice", "alice@example.com", "encodedPassword", UserRole.USER);
        setId(testUser, 1L);
        testTask = new Task("Test Task", "A description", testUser);
        setId(testTask, 100L);
    }

    // =========================================================================
    // UC1: Create a Task
    // =========================================================================

    @Nested
    @DisplayName("POST /api/tasks - Create a Task (UC1)")
    class CreateTaskEndpoint {

        @Test
        @WithMockUser(username = "alice", roles = {"USER"})
        @DisplayName("Should create task and return 201 Created")
        void shouldCreateTaskAndReturn201() throws Exception {
            CreateTaskRequest request = new CreateTaskRequest("New Task", "Description");

            // ARRANGE: mock the service to return our test task.
            // isNull() matches the null dueDate (not provided in the request).
            when(taskService.createTask(any(User.class), eq("New Task"), eq("Description"), isNull()))
                    .thenReturn(testTask);

            // ACT: send a POST request with JSON body
            mockMvc.perform(post("/api/tasks")
                            .with(csrf())                              // CSRF token required for POST
                            .contentType(MediaType.APPLICATION_JSON)   // We're sending JSON
                            .content(objectMapper.writeValueAsString(request)))  // The JSON body
                    // ASSERT: check HTTP status and response body
                    .andExpect(status().isCreated())                   // 201 Created
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Task created"))
                    .andExpect(jsonPath("$.data.title").value("Test Task"))
                    .andExpect(jsonPath("$.data.ownerUsername").value("alice"));

            // ASSERT: verify the service was called with the right arguments
            verify(taskService).createTask(any(User.class), eq("New Task"), eq("Description"), isNull());
        }

        @Test
        @WithMockUser(username = "alice", roles = {"USER"})
        @DisplayName("Should return 400 Bad Request when title is blank")
        void shouldReturn400WhenTitleIsBlank() throws Exception {
            CreateTaskRequest request = new CreateTaskRequest("", "Description");

            mockMvc.perform(post("/api/tasks")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Title")));

            // ASSERT: the service was NEVER called because validation failed first
            verify(taskService, never()).createTask(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should return 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            CreateTaskRequest request = new CreateTaskRequest("Title", "Desc");

            mockMvc.perform(post("/api/tasks")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());

            verify(taskService, never()).createTask(any(), any(), any(), any());
        }
    }

    // =========================================================================
    // UC2: Mark Task as Complete
    // =========================================================================

    @Nested
    @DisplayName("PATCH /api/tasks/{id}/complete - Mark Complete (UC2)")
    class MarkTaskCompleteEndpoint {

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("Should mark task complete and return 200 OK")
        void shouldMarkCompleteAndReturn200() throws Exception {
            doNothing().when(taskService).markTaskComplete(100L);

            mockMvc.perform(patch("/api/tasks/100/complete").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Task marked as complete"));

            verify(taskService).markTaskComplete(100L);
        }

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("Should return 404 when task not found")
        void shouldReturn404WhenTaskNotFound() throws Exception {
            doThrow(new EntityNotFoundException("Task not found: 999"))
                    .when(taskService).markTaskComplete(999L);

            mockMvc.perform(patch("/api/tasks/999/complete").with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Task not found: 999"));
        }
    }

    // =========================================================================
    // UC3: View Tasks (Paginated + Filtered)
    // =========================================================================

    @Nested
    @DisplayName("GET /api/tasks - View Tasks (UC3)")
    class ViewTasksEndpoint {

        @Test
        @WithMockUser(username = "alice", roles = {"USER"})
        @DisplayName("Should return user's tasks as paginated JSON")
        void shouldReturnUsersTasks() throws Exception {
            // isNull() matches the absent "complete" query parameter (no filter)
            when(taskService.findAllTasksByUserAndStatus(any(User.class), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(testTask)));

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content[0].title").value("Test Task"))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("Should return empty page when user has no tasks")
        void shouldReturnEmptyArrayWhenNoTasks() throws Exception {
            when(taskService.findAllTasksByUserAndStatus(any(User.class), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    // =========================================================================
    // UC5: Assign Task to User
    // =========================================================================

    @Nested
    @DisplayName("PATCH /api/tasks/assign - Assign Task (UC5)")
    class AssignTaskEndpoint {

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("Should assign task and return 200 OK")
        void shouldAssignTaskAndReturn200() throws Exception {
            String requestBody = """
                {
                    "taskId": 100,
                    "userId": 2
                }
                """;

            doNothing().when(taskService).assignTaskToUser(100L, 2L);

            mockMvc.perform(patch("/api/tasks/assign")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Task assigned successfully"));

            verify(taskService).assignTaskToUser(100L, 2L);
        }

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("Should return 404 when task not found")
        void shouldReturn404WhenTaskNotFound() throws Exception {
            doThrow(new EntityNotFoundException("Task not found: 999"))
                    .when(taskService).assignTaskToUser(999L, 2L);

            String requestBody = """
                {
                    "taskId": 999,
                    "userId": 2
                }
                """;

            mockMvc.perform(patch("/api/tasks/assign")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Task not found: 999"));
        }

        @Test
        @WithMockUser(roles = {"USER"})
        @DisplayName("Should return 400 when taskId is null")
        void shouldReturn400WhenTaskIdIsNull() throws Exception {
            String requestBody = """
                {
                    "taskId": null,
                    "userId": 2
                }
                """;

            mockMvc.perform(patch("/api/tasks/assign")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            // Service must never be called — validation fails before controller logic runs
            verify(taskService, never()).assignTaskToUser(anyLong(), anyLong());
        }
    }

    // =========================================================================
    // Authorization Tests
    // =========================================================================

    @Nested
    @DisplayName("Authorization - Access Control")
    class AuthorizationTests {

        @Test
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        @DisplayName("Admin should access user endpoints (role hierarchy: ADMIN > USER)")
        void adminShouldAccessUserEndpoints() throws Exception {
            when(taskService.findAllTasksByUserAndStatus(any(User.class), isNull(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(testTask)));

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("Unauthenticated user should receive 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // Helper: Set ID via reflection
    // =========================================================================
    // In production, JPA assigns IDs. In tests, we inject a fake ID manually.

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