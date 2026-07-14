/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.config;

import io.github.lussac7.taskmanager.domain.Task;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.domain.UserRole;
import io.github.lussac7.taskmanager.repository.TaskRepository;
import io.github.lussac7.taskmanager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seeds the database with demo users and tasks on first startup.
 *
 * <p><b>Security:</b> Hardcoded credentials are for LOCAL DEVELOPMENT ONLY.
 * This bean is disabled in production by {@code @Profile("!prod")}.
 * Passwords are BCrypt-hashed before storage — raw values never touch the database.</p>
 *
 * <p><b>Demo accounts:</b> alice/password123 (USER), bob/password123 (USER),
 * admin/admin123 (ADMIN).</p>
 *
 * <p><b>Idempotent:</b> Skipped if the database already contains users.</p>
 *
 * @see SecurityConfig
 */
@Configuration
@Profile("!prod")  // This bean is only created when the "prod" profile is NOT active.
// In production, users are inserted directly into the database.
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * Returns a runner that creates sample data after the application context is ready.
     *
     * @param userRepository  for persisting users
     * @param taskRepository  for persisting tasks
     * @param passwordEncoder BCrypt encoder from {@link SecurityConfig}
     */
    @Bean
    @SuppressWarnings("java:S2068")  // Dev-only credentials — never deployed to production
    CommandLineRunner initData(UserRepository userRepository,
                               TaskRepository taskRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {

            // =================================================================
            // IDEMPOTENCY GUARD: Skip if the database already has users
            // =================================================================
            // Why check? If you restart the app, this runner would try to
            // insert the same users again, causing a "duplicate key" error.
            // By checking userRepository.count(), we skip if users already exist.
            // This also protects real user data if someone accidentally runs
            // with the dev profile against a production database.
            if (userRepository.count() > 0) {
                log.info("Database already contains data. Skipping initialization.");
                return;  // Exit early — don't insert anything
            }

            log.info("Initializing sample data for development...");

            // =================================================================
            // CREATE DEMO USERS
            // =================================================================
            // Passwords are BCrypt-hashed before storage. The raw strings you
            // see here (like "password123") are passed through passwordEncoder.encode()
            // immediately. The database NEVER stores the raw password.
            //
            // During login, Spring Security hashes the submitted password and
            // compares the hashes — plaintext comparison never occurs.
            //
            // NOSONAR markers tell SonarQube: "We know these are hardcoded.
            // This is intentional and safe because @Profile("!prod") blocks
            // this bean from loading in production."

            // Alice: Primary test user. Notifications ENABLED.
            // She owns two demo tasks and is the main account for manual testing.
            // NOSONAR: java/S2068 — dev credentials, blocked by @Profile("!prod")
            User alice = new User("alice", "alice@example.com",
                    passwordEncoder.encode("password123"), UserRole.USER);  // NOSONAR
            alice.setNotificationEnabled(true);

            // Bob: Secondary test user. Notifications DISABLED.
            // Used to test the opt[] guard condition in the Sequence Diagrams.
            // When Bob is assigned a task, no notification is sent.
            // NOSONAR: Bob has notifications disabled — used to test opt[] guard
            User bob = new User("bob", "bob@example.com",
                    passwordEncoder.encode("password123"), UserRole.USER);  // NOSONAR
            bob.setNotificationEnabled(false);

            // Admin: Administrator account with ROLE_ADMIN.
            // ROLE_ADMIN inherits ROLE_USER via the RoleHierarchy in SecurityConfig.
            // Can delete tasks and manage users.
            // NOSONAR: ROLE_ADMIN inherits ROLE_USER via the role hierarchy
            User admin = new User("admin", "admin@example.com",
                    passwordEncoder.encode("admin123"), UserRole.ADMIN);  // NOSONAR
            admin.setNotificationEnabled(true);

            // Persist all three users to the database.
            // JPA assigns their IDs (1, 2, 3) during save().
            userRepository.save(alice);
            userRepository.save(bob);
            userRepository.save(admin);

            // =================================================================
            // CREATE DEMO TASKS
            // =================================================================
            // All tasks start as incomplete (isComplete = false).
            // This means the UI shows "Complete" and "Assign" buttons for them.
            //
            // Alice gets two tasks, Bob gets one.
            // This demonstrates: multiple tasks per user, and tasks owned by
            // different users appearing only in their respective dashboards.

            taskRepository.save(new Task("Complete project report",
                    "Write the Q2 project summary", alice));
            taskRepository.save(new Task("Review pull request",
                    "Review Bob's PR #42", alice));
            taskRepository.save(new Task("Update documentation",
                    "Update the API docs", bob));

            // =================================================================
            // LOG CREDENTIALS FOR THE DEVELOPER
            // =================================================================
            // These appear in the console when the application starts.
            // The developer can copy-paste them to log in quickly.
            log.info("--- Sample Data Initialized ---");
            log.info("  alice / password123  (USER, notifications ON)");
            log.info("  bob   / password123  (USER, notifications OFF)");
            log.info("  admin / admin123    (ADMIN, notifications ON)");
            log.info("  {} sample tasks created", taskRepository.count());
            //log.info("  Access: http://localhost:8080/index.html");
            log.info("  Access front-end page: http://localhost:3000/index.html");
            log.info("------------------------------");
        };
    }
}