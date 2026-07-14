/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.controller;

import io.github.lussac7.taskmanager.config.CurrentUserArgumentResolver;
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for {@link UserController}.
 *
 * <p>Tests the HTTP layer in isolation. The real UserController is tested, but
 * all its dependencies (repositories, password encoder) are replaced by mocks.
 * No database, no real password hashing — just HTTP request → response verification.</p>
 *
 * <p><b>What we're testing:</b></p>
 * <ul>
 *   <li>Registration returns 201 for new users, 400 for duplicates.</li>
 *   <li>Admin can list all users (200).</li>
 *   <li>Admin can create users with any role (201).</li>
 *   <li>Admin can delete users (200).</li>
 * </ul>
 *
 * @see UserController
 */
@WebMvcTest(UserController.class)
@Import(CurrentUserArgumentResolver.class)
@DisplayName("UserController Web Layer Tests")
class UserControllerTest {

    // =========================================================================
    // TEST DEPENDENCIES
    // =========================================================================
    // @MockitoBean creates a mock AND registers it in the Spring test context.
    // When UserController calls userRepository.save(...), it calls THIS fake,
    // not the real repository. We control exactly what the fake returns.

    @Autowired
    private MockMvc mockMvc;           // Simulates HTTP requests

    @MockitoBean
    private UserRepository userRepository;  // Fake database for users

    @MockitoBean
    private PasswordEncoder passwordEncoder; // Fake password hasher

    @MockitoBean
    private TaskRepository taskRepository;   // Needed for user deletion (unassign tasks)

    @MockitoBean
    private AuditRepository auditRepository; // Needed for user deletion (delete audit entries)

    // =========================================================================
    // TEST FIXTURES
    // =========================================================================

    /** A fake user object used as the return value from mocked repository calls. */
    private User testUser;

    /**
     * Runs BEFORE each @Test method.
     * Creates a fresh test user so tests don't interfere with each other.
     */
    @BeforeEach
    void setUp() {
        // Create a user with a known username, email, and role.
        // The password is a placeholder — in real code it would be BCrypt-hashed.
        testUser = new User("alice", "alice@test.com", "encoded", UserRole.USER);

        // IDs are normally assigned by JPA when an entity is persisted.
        // In tests, there's no database, so we inject a fake ID via reflection.
        setId(testUser, 1L);
    }

    // =========================================================================
    // REGISTRATION TESTS
    // =========================================================================
    // These test the PUBLIC registration endpoint: POST /api/users/register
    // Anyone can call this — no authentication required.
    // @WithMockUser is added just to have a valid CSRF token (the endpoint
    // itself is public, but CSRF protection still applies to POST requests).

    @Nested
    @DisplayName("POST /api/users/register - Public Registration")
    class Register {

        /**
         * Happy path: a new user registers with a unique username.
         *
         * <p>What we test:
         * <ul>
         *   <li>The endpoint returns HTTP 201 Created.</li>
         *   <li>The response contains "success": true.</li>
         *   <li>The service methods were called with the correct arguments.</li>
         * </ul></p>
         *
         * <p><b>Mock setup explained:</b>
         * <ul>
         *   <li>existsByUsername("newuser") → false (username is available)</li>
         *   <li>encode(any password) → "hashed_password" (simulate BCrypt)</li>
         *   <li>save(any User) → testUser (simulate database save)</li>
         * </ul></p>
         */
        @Test
        @WithMockUser  // Provides a valid CSRF token (needed for POST requests)
        @DisplayName("Should register a new user and return 201")
        void shouldRegisterNewUser() throws Exception {
            // ARRANGE: set up the mocks
            when(userRepository.existsByUsername("newuser")).thenReturn(false);     // Username available
            when(passwordEncoder.encode(any())).thenReturn("hashed_password");      // Simulate BCrypt
            when(userRepository.save(any(User.class))).thenReturn(testUser);        // Simulate save

            // ACT: send a POST request with JSON body
            mockMvc.perform(post("/api/users/register")
                            .with(csrf())                              // CSRF token (required for POST)
                            .contentType(MediaType.APPLICATION_JSON)   // We're sending JSON
                            .content("""
                            {"username":"newuser","email":"new@test.com","password":"pass123"}
                            """))
                    // ASSERT: check the response
                    .andExpect(status().isCreated())                   // HTTP 201
                    .andExpect(jsonPath("$.success").value(true));     // success = true
        }

        /**
         * Error path: trying to register with a username that already exists.
         *
         * <p>The controller checks existsByUsername() BEFORE creating the user.
         * If it returns true, the controller returns 400 immediately.</p>
         */
        @Test
        @WithMockUser
        @DisplayName("Should return 400 when username already exists")
        void shouldReturn400ForDuplicateUsername() throws Exception {
            // ARRANGE: the username "alice" is already taken
            when(userRepository.existsByUsername("alice")).thenReturn(true);

            // ACT & ASSERT: should get 400 Bad Request
            mockMvc.perform(post("/api/users/register")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                            {"username":"alice","email":"alice@test.com","password":"pass123"}
                            """))
                    .andExpect(status().isBadRequest())                // HTTP 400
                    .andExpect(jsonPath("$.success").value(false));    // success = false
        }
    }

    // =========================================================================
    // LIST USERS TESTS (Admin Only)
    // =========================================================================

    @Nested
    @DisplayName("GET /api/users - List Users")
    class ListUsers {

        /**
         * An admin requests the list of all users.
         *
         * <p>@WithMockUser(roles = {"ADMIN"}) simulates an authenticated admin.
         * The mock repository returns a list with one user. The real controller
         * masks all passwords to "[PROTECTED]" before returning the response.</p>
         */
        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("Should return all users")
        void shouldReturnAllUsers() throws Exception {
            // ARRANGE: repository returns a list with our test user
            when(userRepository.findAll()).thenReturn(List.of(testUser));

            // ACT & ASSERT
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isOk())                        // HTTP 200
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())            // data is an array
                    .andExpect(jsonPath("$.data[0].username").value("alice"));
        }
    }

    // =========================================================================
    // CREATE USER TESTS (Admin Only)
    // =========================================================================

    @Nested
    @DisplayName("POST /api/users - Create User (Admin)")
    class CreateUser {

        /**
         * An admin creates a new user with a specified role.
         *
         * <p>Unlike registration (which always creates USER role), admins can
         * create users with any role by including "role":"ADMIN" in the request.</p>
         */
        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("Should create user and return 201")
        void shouldCreateUser() throws Exception {
            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            mockMvc.perform(post("/api/users")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"username":"newuser","email":"new@test.com","password":"pass123","role":"USER"}
                                """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // =========================================================================
    // DELETE USER TESTS (Admin Only)
    // =========================================================================

    @Nested
    @DisplayName("DELETE /api/users/{id} - Delete User (Admin)")
    class DeleteUser {

        /**
         * An admin deletes an existing user.
         *
         * <p><b>Why we mock taskRepository and auditRepository:</b>
         * The real UserController.deleteUser() method needs to clean up related
         * records before deleting the user (unassign tasks, delete audit entries).
         * In this test, we mock those repositories to return empty lists,
         * simulating a user with no related records.</p>
         */
        @Test
        @WithMockUser(roles = {"ADMIN"})
        @DisplayName("Should delete user and return 200")
        void shouldDeleteUser() throws Exception {
            // ARRANGE: user exists in the database
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

            // ARRANGE: the user has no related records (empty lists)
            // PageImpl is Spring Data's implementation of the Page interface.
            // We create an empty page to simulate "no tasks assigned to this user".
            when(taskRepository.findAllByAssignedTo(any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            // Empty list = "no audit entries for this user"
            when(auditRepository.findAllByActorId(any())).thenReturn(List.of());

            // ACT & ASSERT
            mockMvc.perform(delete("/api/users/1").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // =========================================================================
    // HELPER: Set ID via reflection
    // =========================================================================
    // In production, JPA assigns IDs when entities are persisted to the database.
    // In unit tests, there's no database, so we manually inject a fake ID.
    // Reflection lets us set a private field without adding a public setter
    // (which would break encapsulation in production code).

    /**
     * Sets the private {@code id} field on a domain object via reflection.
     * Used because JPA normally assigns IDs during persistence, but tests
     * don't have a database.
     *
     * @param target the domain object whose ID to set
     * @param id     the fake ID to assign
     */
    private void setId(Object target, Long id) {
        try {
            // Step 1: Get the Field object representing the "id" field
            var idField = target.getClass().getDeclaredField("id");
            // Step 2: Make it accessible (it's private, so normally we can't touch it)
            idField.setAccessible(true);
            // Step 3: Set the value on our target object
            idField.set(target, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}