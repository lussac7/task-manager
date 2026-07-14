/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.controller;

import io.github.lussac7.taskmanager.domain.AuditEntry;
import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.domain.UserRole;
import io.github.lussac7.taskmanager.dto.AppResponse;
import io.github.lussac7.taskmanager.repository.AuditRepository;
import io.github.lussac7.taskmanager.repository.TaskRepository;
import io.github.lussac7.taskmanager.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for user management.
 *
 * <p><b>Admin endpoints</b> (require ROLE_ADMIN): list, create, change role, delete.</p>
 * <p><b>Public endpoints:</b> self-registration (POST /api/users/register).</p>
 *
 * <p>Passwords are BCrypt-hashed before storage. Raw passwords are NEVER persisted.</p>
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User management endpoints (admin only, except registration)")
@SecurityRequirement(name = "BasicAuth")
@PreAuthorize("hasRole('ADMIN')")  // Class-level: all methods require ADMIN unless overridden
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================
    // UserRepository: standard user CRUD
    // PasswordEncoder: BCrypt hashing for passwords
    // TaskRepository: needed to unassign tasks before deleting a user
    // AuditRepository: needed to delete audit entries before deleting a user

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TaskRepository taskRepository;
    private final AuditRepository auditRepository;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          TaskRepository taskRepository, AuditRepository auditRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.taskRepository = taskRepository;
        this.auditRepository = auditRepository;
    }

    // =========================================================================
    // DTO: CreateUserRequest
    // =========================================================================
    // Nested DTO because it's only used by this controller.
    // In a larger project, this would be in its own file.

    /**
     * Request body for creating a new user (admin or self-registration).
     */
    public static class CreateUserRequest {
        @NotBlank @Size(min = 3, max = 50)
        public String username;

        @NotBlank @Size(min = 6, max = 100)
        public String password;  // Raw password — will be BCrypt-hashed before storage

        @NotBlank
        public String email;

        public String role = "USER";  // Default: regular user. Admins can set to "ADMIN".
    }

    // =========================================================================
    // LIST USERS (Admin)
    // =========================================================================

    /** Lists all users. Admin only. Passwords are masked for security. */
    @Operation(summary = "List all users")
    @ApiResponse(responseCode = "200", description = "Users retrieved")
    @GetMapping
    public ResponseEntity<AppResponse<List<User>>> listUsers() {
        List<User> users = userRepository.findAll();
        // NEVER return password hashes in API responses.
        // Even though they're BCrypt-hashed, they should stay on the server.
        users.forEach(u -> u.setPassword("[PROTECTED]"));
        return ResponseEntity.ok(AppResponse.success("Users retrieved", users));
    }

    // =========================================================================
    // CREATE USER (Admin)
    // =========================================================================

    /**
     * Creates a new user with any role. Admin only.
     * Password is BCrypt-hashed before storage.
     */
    @Operation(summary = "Create a new user")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400", description = "Username already exists")
    @PostMapping
    public ResponseEntity<AppResponse<Map<String, Object>>> createUser(
            @Valid @RequestBody CreateUserRequest request) {

        // --- Duplicate check ---
        if (userRepository.existsByUsername(request.username)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AppResponse.error("Username already exists: " + request.username));
        }

        // --- Determine role ---
        // Only admins can create other admins. Self-registration always creates USER.
        UserRole role = "ADMIN".equalsIgnoreCase(request.role) ? UserRole.ADMIN : UserRole.USER;

        // --- Create and persist ---
        // The password is hashed HERE, before it ever touches the database.
        User user = new User(
                request.username,
                request.email,
                passwordEncoder.encode(request.password),  // BCrypt hash
                role
        );
        user = userRepository.save(user);  // JPA assigns the ID

        log.info("User created: {} (role={})", user.getUsername(), role);

        // Return a clean response (no password hash)
        Map<String, Object> response = Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", user.getRole().name()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.success("User created", response));
    }

    // =========================================================================
    // CHANGE ROLE (Admin)
    // =========================================================================

    /** Changes a user's role between USER and ADMIN. Admin only. */
    @Operation(summary = "Change user role")
    @PatchMapping("/{id}/role")
    public ResponseEntity<AppResponse<Void>> changeRole(
            @PathVariable Long id,
            @RequestParam String role) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        UserRole newRole = "ADMIN".equalsIgnoreCase(role) ? UserRole.ADMIN : UserRole.USER;
        user.setRole(newRole);
        userRepository.save(user);

        log.info("User role changed: {} → {}", user.getUsername(), newRole);
        return ResponseEntity.ok(AppResponse.success("Role updated"));
    }

    // =========================================================================
    // SELF-REGISTRATION (Public)
    // =========================================================================

    /**
     * Self-registration endpoint — NO authentication required.
     * Always creates a USER role account (never ADMIN).
     * The @PreAuthorize("permitAll()") overrides the class-level ADMIN requirement.
     */
    @Operation(summary = "Register a new account")
    @ApiResponse(responseCode = "201", description = "Account created")
    @PostMapping("/register")
    @PreAuthorize("permitAll()")  // Override class-level @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<Map<String, Object>>> register(
            @Valid @RequestBody CreateUserRequest request) {

        // Prevent duplicate usernames
        if (userRepository.existsByUsername(request.username)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AppResponse.error("Username already exists"));
        }

        // ALWAYS create as USER — never allow self-registration as ADMIN.
        // Admin accounts must be created by existing admins via the POST /api/users endpoint.
        User user = new User(
                request.username,
                request.email,
                passwordEncoder.encode(request.password),
                UserRole.USER
        );
        user = userRepository.save(user);

        Map<String, Object> response = Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "role", "USER"
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppResponse.success("Account created", response));
    }

    // =========================================================================
    // DELETE USER (Admin)
    // =========================================================================

    /**
     * Deletes a user and all related records.
     *
     * <p><b>Why so many steps?</b> The database has foreign key constraints.
     * We can't delete a user while other tables still reference them.
     * The order matters:
     * <ol>
     *   <li>Unassign tasks assigned TO this user (set assignedTo = null)</li>
     *   <li>Delete audit entries where this user is the actor</li>
     *   <li>Delete the user (cascade removes their owned tasks)</li>
     * </ol></p>
     */
    @Operation(summary = "Delete a user")
    @ApiResponse(responseCode = "200", description = "User deleted")
    @ApiResponse(responseCode = "404", description = "User not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<AppResponse<Void>> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        String username = user.getUsername();

        // Step 1: Unassign all tasks assigned to this user.
        // If we don't do this, the foreign key "assigned_to_id → users.id"
        // will block the deletion.
        List<Task> assignedTasks = taskRepository.findAllByAssignedTo(user, Pageable.unpaged()).getContent();
        for (Task task : assignedTasks) {
            task.setAssignedTo(null);  // Remove the assignment
            taskRepository.save(task);
        }

        // Step 2: Delete audit entries where this user is the actor.
        // Foreign key: audit_entries.actor_id → users.id
        List<AuditEntry> auditEntries = auditRepository.findAllByActorId(id);
        auditRepository.deleteAll(auditEntries);

        // Step 3: Delete the user.
        // cascade = ALL on the "ownedTasks" relationship means all tasks
        // OWNED by this user will also be deleted automatically.
        userRepository.delete(user);

        log.info("User deleted via API: {} (id={})", username, id);
        return ResponseEntity.ok(AppResponse.success("User '" + username + "' deleted"));
    }
}