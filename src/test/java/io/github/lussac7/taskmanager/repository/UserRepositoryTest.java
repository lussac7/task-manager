/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.repository;

import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link UserRepository} — real H2 database, no mocks.
 *
 * <h2>What @DataJpaTest does:</h2>
 * <p>Creates a SLICED Spring context with ONLY JPA beans (entities, repositories,
 * in-memory H2). No controllers, services, or Spring Security are loaded.
 * This makes tests fast (~50ms each) while testing real database operations.</p>
 *
 * <h2>Why test UserRepository separately?</h2>
 * <p>UserRepository is used on EVERY login attempt by Spring Security's
 * UserDetailsServiceImpl. If findByUsername doesn't work, authentication breaks.
 * These tests verify the query derivation works correctly against a real database.</p>
 *
 * @see UserRepository
 * @see User
 */
@DataJpaTest
@DisplayName("UserRepository Integration Tests")
class UserRepositoryTest {

    // =========================================================================
    // TEST DEPENDENCIES
    // =========================================================================

    /**
     * TestEntityManager: direct JPA operations for test setup.
     * persist() inserts immediately, flush() forces SQL execution,
     * clear() detaches entities for fresh reads.
     */
    @Autowired
    private TestEntityManager entityManager;

    /**
     * The REAL UserRepository — not a mock. Spring Data JPA generates
     * the implementation at runtime.
     */
    @Autowired
    private UserRepository userRepository;

    // =========================================================================
    // SETUP: Create a known user before each test
    // =========================================================================

    /**
     * Creates a user "alice" in the database before each test.
     * flush() ensures the INSERT is executed immediately.
     * clear() detaches all entities so subsequent queries read fresh from
     * the database, proving the data was actually persisted.
     */
    @BeforeEach
    void setUp() {
        User user = new User("alice", "alice@example.com", "encodedPass", UserRole.USER);
        entityManager.persist(user);
        entityManager.flush();   // Force SQL INSERT to execute NOW
        entityManager.clear();   // Detach everything for truly fresh reads
    }

    // =========================================================================
    // FIND BY USERNAME TESTS
    // =========================================================================
    // This is the most critical query in the entire system.
    // It's called by Spring Security on EVERY login attempt.

    @Nested
    @DisplayName("findByUsername — called on every login by UserDetailsServiceImpl")
    class FindByUsername {

        /**
         * Finding an existing username should return the user.
         *
         * <p><b>How Spring Data JPA turns this into SQL:</b>
         * <pre>
         * findByUsername("alice")
         *   → SELECT * FROM users WHERE username = 'alice'
         * </pre>
         * The method name is parsed at application startup. No SQL needed.</p>
         */
        @Test
        @DisplayName("Should find user by existing username")
        void shouldFindByExistingUsername() {
            Optional<User> found = userRepository.findByUsername("alice");

            // The user should be present with the correct email
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
        }

        /**
         * Finding a non-existent username should return Optional.empty().
         *
         * <p><b>Why is this critical?</b> If the repository returned null instead
         * of Optional.empty(), the calling code in UserDetailsServiceImpl
         * (.orElseThrow(...)) would throw a NullPointerException instead of
         * the expected UsernameNotFoundException. Spring Security wouldn't
         * know the login failed — the user would see a 500 error instead of
         * "Invalid username or password."</p>
         */
        @Test
        @DisplayName("Should return empty when username not found")
        void shouldReturnEmptyForUnknownUsername() {
            // WHEN: looking up a username that doesn't exist
            Optional<User> found = userRepository.findByUsername("nonexistent");

            // THEN: must return Optional.empty(), NEVER null
            assertThat(found).isEmpty();
        }
    }

    // =========================================================================
    // EXISTS BY USERNAME TESTS
    // =========================================================================
    // Used during registration to prevent duplicate usernames.
    // More efficient than findByUsername because it only checks existence,
    // it doesn't load the full entity.

    @Nested
    @DisplayName("existsByUsername — for duplicate username validation")
    class ExistsByUsername {

        /**
         * An existing username should return true.
         *
         * <p><b>Generated SQL:</b>
         * <pre>
         * existsByUsername("alice")
         *   → SELECT COUNT(*) > 0 FROM users WHERE username = 'alice'
         * </pre></p>
         */
        @Test
        @DisplayName("Should return true for existing username")
        void shouldReturnTrueForExistingUsername() {
            assertThat(userRepository.existsByUsername("alice")).isTrue();
        }

        /**
         * A non-existent username should return false.
         * Used during registration: if false, the username is available.
         */
        @Test
        @DisplayName("Should return false for non-existing username")
        void shouldReturnFalseForNonExistingUsername() {
            assertThat(userRepository.existsByUsername("ghost")).isFalse();
        }
    }
}