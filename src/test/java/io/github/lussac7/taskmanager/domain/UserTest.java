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
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link User} — domain behavior + Spring Security integration.
 * No Spring context needed.
 *
 * <p>These tests verify TWO aspects of the User class:</p>
 * <ol>
 *   <li><b>Domain behavior:</b> Roles, notification preferences, isAdmin().</li>
 *   <li><b>Spring Security integration:</b> Does the User correctly implement
 *       the UserDetails interface? Do authorities map correctly?</li>
 * </ol>
 *
 * <p><b>Why no Spring context?</b> The User class implements UserDetails directly.
 * We can test the interface contract with plain Java — no need to start a
 * Spring application.</p>
 *
 * @see User
 * @see UserRole
 */
@DisplayName("User Entity Unit Tests")
class UserTest {

    // =========================================================================
    // TEST CONSTANTS
    // =========================================================================

    /**
     * A valid BCrypt hash used for creating test User objects.
     * The actual password this represents doesn't matter — we're testing
     * the User class behavior, not password validation (that's Spring Security's job).
     */
    private static final String ENCODED_PASSWORD =
            "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    // =========================================================================
    // CONSTRUCTION TESTS
    // =========================================================================
    // Verify that a newly created User has the correct role, admin status,
    // and notification preference.

    @Nested
    @DisplayName("User Construction")
    class Construction {

        /**
         * A user created with USER role should not be considered an admin.
         * isAdmin() is a convenience method that checks role == ADMIN.
         */
        @Test
        @DisplayName("Should create regular user with USER role")
        void shouldCreateUserWithDefaultRole() {
            User user = new User("alice", "alice@example.com", ENCODED_PASSWORD, UserRole.USER);

            assertThat(user.getRole()).isEqualTo(UserRole.USER);
            assertThat(user.isAdmin()).isFalse();  // USER role → not admin
        }

        /**
         * A user created with ADMIN role should be recognized as an admin.
         */
        @Test
        @DisplayName("Should create admin user with ADMIN role")
        void shouldCreateAdminUser() {
            User admin = new User("admin", "admin@example.com", ENCODED_PASSWORD, UserRole.ADMIN);

            assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(admin.isAdmin()).isTrue();  // ADMIN role → is admin
        }

        /**
         * Notifications should be ON by default (opt-out model).
         * Users must explicitly disable them if they don't want notifications.
         * This is checked by the opt[] guard in Sequence Diagrams.
         */
        @Test
        @DisplayName("Should enable notifications by default")
        void shouldEnableNotificationsByDefault() {
            User user = new User("bob", "bob@example.com", ENCODED_PASSWORD, UserRole.USER);

            assertThat(user.isNotificationEnabled()).isTrue();
        }
    }

    // =========================================================================
    // SPRING SECURITY INTEGRATION TESTS
    // =========================================================================
    // These test that the User class correctly implements the UserDetails
    // interface. Spring Security calls these methods during authentication
    // and authorization.

    @Nested
    @DisplayName("Spring Security Integration (UserDetails)")
    class UserDetailsIntegration {

        /** A test user with USER role, created fresh for each test. */
        private User user;

        @BeforeEach
        void setUp() {
            user = new User("testuser", "test@example.com", ENCODED_PASSWORD, UserRole.USER);
        }

        /**
         * A USER role should produce exactly one authority: ROLE_USER.
         *
         * <p><b>How authorities work:</b></p>
         * <ol>
         *   <li>User.getRole() returns UserRole.USER</li>
         *   <li>getAuthorities() creates: new SimpleGrantedAuthority("ROLE_" + "USER")</li>
         *   <li>The result is "ROLE_USER"</li>
         *   <li>Spring Security checks this against SecurityConfig rules like
         *       .hasRole("USER") which looks for ROLE_USER</li>
         * </ol>
         *
         * <p><b>.extracting()</b> is an AssertJ method that pulls a specific
         * property from each element in a collection. Here we extract the
         * authority string from each GrantedAuthority object.</p>
         */
        @Test
        @DisplayName("Should return ROLE_USER authority for USER role")
        void shouldReturnUserAuthorities() {
            // WHEN: getting authorities for a regular user
            var authorities = user.getAuthorities();

            // THEN: should have exactly one authority: ROLE_USER
            assertThat(authorities)
                    .hasSize(1)                                          // Only one authority
                    .extracting(GrantedAuthority::getAuthority)          // Get the string from each
                    .containsExactly("ROLE_USER");                       // Must be exactly this
        }

        /**
         * An ADMIN role should produce ROLE_ADMIN.
         *
         * <p>Note: ROLE_ADMIN > ROLE_USER is configured in SecurityConfig's
         * roleHierarchy(), NOT in the User entity. The entity only provides
         * the base role.</p>
         */
        @Test
        @DisplayName("Should return ROLE_ADMIN authority for ADMIN role")
        void shouldReturnAdminAuthorities() {
            User admin = new User("admin", "admin@example.com", ENCODED_PASSWORD, UserRole.ADMIN);

            var authorities = admin.getAuthorities();

            assertThat(authorities)
                    .extracting(GrantedAuthority::getAuthority)
                    .contains("ROLE_ADMIN");
        }

        // =====================================================================
        // ACCOUNT STATUS TESTS
        // =====================================================================
        // The UserDetails interface requires four boolean status methods.
        // In this application, they all return true because we don't implement
        // account expiration, locking, credential rotation, or disabling.
        //
        // If you add these features later, these tests would need to be updated
        // to check the actual field values instead of always expecting true.

        @Test @DisplayName("Account should be non-expired")
        void accountShouldBeNonExpired() {
            assertThat(user.isAccountNonExpired()).isTrue();
        }

        @Test @DisplayName("Account should be non-locked")
        void accountShouldBeNonLocked() {
            assertThat(user.isAccountNonLocked()).isTrue();
        }

        @Test @DisplayName("Credentials should be non-expired")
        void credentialsShouldBeNonExpired() {
            assertThat(user.isCredentialsNonExpired()).isTrue();
        }

        @Test @DisplayName("Account should be enabled")
        void accountShouldBeEnabled() {
            assertThat(user.isEnabled()).isTrue();
        }
    }

    // =========================================================================
    // NOTIFICATION PREFERENCE TESTS
    // =========================================================================
    // Verifies that users can opt out of notifications.
    // This preference is checked in the Sequence Diagrams' opt[] fragments.

    @Nested
    @DisplayName("Notification Preferences")
    class NotificationPreferences {

        /**
         * A user who disables notifications should have isNotificationEnabled() == false.
         *
         * <p>This maps to the guard condition in the Sequence Diagram:
         * <pre>
         * opt [user.isNotificationEnabled() == true]
         *     → send notification
         * end
         * </pre>
         * When a user disables notifications, the opt fragment evaluates to false
         * and no notification is sent.</p>
         */
        @Test
        @DisplayName("Should disable notifications when set to false")
        void shouldDisableNotifications() {
            // GIVEN: a user with notifications currently enabled
            User user = new User("carol", "carol@example.com", ENCODED_PASSWORD, UserRole.USER);
            assertThat(user.isNotificationEnabled()).isTrue();  // Default is true

            // WHEN: the user disables notifications
            user.setNotificationEnabled(false);

            // THEN: notifications should be disabled
            assertThat(user.isNotificationEnabled()).isFalse();
        }
    }
}