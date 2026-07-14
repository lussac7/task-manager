/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.config;

import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.domain.UserRole;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Injects our domain {@link User} entity into {@code @AuthenticationPrincipal} parameters.
 *
 * <p>Spring Security's default resolver returns a {@code UserDetails}. This resolver
 * replaces that behavior for parameters typed as {@link User}, injecting the JPA
 * entity directly — no manual cast needed.</p>
 *
 * <p><b>Production:</b> principal is already our User entity → returned directly.</p>
 * <p><b>Testing:</b> {@code @WithMockUser} creates a Spring Security UserDetails →
 * a temporary User is built as a test double.</p>
 *
 * @see User
 * @see WebMvcConfig (where this resolver is registered)
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    // =========================================================================
    // Step 1: Tell Spring WHEN to call this resolver
    // =========================================================================
    // Spring MVC asks every registered resolver: "Do you handle this parameter?"
    // We say YES only for parameters of type User. For Long, String, etc., we
    // return false and let other resolvers handle them.

    /** Claims only parameters of type {@link User}. */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // Example: controller method is "createTask(@AuthenticationPrincipal User user, ...)"
        // → parameter.getParameterType() returns User.class → we return true
        // Example: controller method has "Long taskId" → returns false
        return parameter.getParameterType().equals(User.class);
    }

    // =========================================================================
    // Step 2: Actually GET the User from the security context
    // =========================================================================
    // Spring calls this when it needs the actual User object for a controller.
    // We look in the SecurityContextHolder — a ThreadLocal "locker" that stores
    // who is currently authenticated for this request.

    /**
     * Extracts the current user from the security context.
     *
     * @return the authenticated {@link User}, a test double, or {@code null} if anonymous
     */
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        // ---------------------------------------------------------------------
        // Get the "digital ID badge" from Spring Security
        // ---------------------------------------------------------------------
        // SecurityContextHolder uses ThreadLocal internally — each HTTP request
        // gets its own thread, so this is safe even with many simultaneous users.
        // Think of it like a wall-mounted badge reader at an office:
        //   - getContext() = look at the badge reader
        //   - getAuthentication() = take the badge out and read it
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        // If nobody is logged in (the badge reader is empty), return null.
        // Spring Security's filter chain should have already blocked this request
        // before it reaches the controller, so this is a safety net.
        if (authentication == null) {
            return null;
        }

        // ---------------------------------------------------------------------
        // Extract the "person" from the badge
        // ---------------------------------------------------------------------
        // The principal is the actual identity stored inside the authentication.
        // In production, this is our User entity (loaded from the database).
        // In tests, this is Spring Security's own User class (not ours!).
        Object principal = authentication.getPrincipal();

        // ---------------------------------------------------------------------
        // PATH A: Production — principal is already our domain User
        // ---------------------------------------------------------------------
        // When a real user logs in via the login form, UserDetailsServiceImpl
        // loads our User entity from the database. Spring Security stores it
        // as the principal. No conversion needed — return it directly.
        //
        // "instanceof User domainUser" is a Java 16+ feature called pattern matching.
        // It checks the type AND creates a variable in one step.
        if (principal instanceof User domainUser) {
            return domainUser;
        }

        // ---------------------------------------------------------------------
        // PATH B: Testing — principal is Spring Security's UserDetails
        // ---------------------------------------------------------------------
        // @WithMockUser creates an org.springframework.security.core.userdetails.User
        // which is NOT our domain entity. If we returned it as-is, the controller
        // would get a ClassCastException because it expects our User class.
        //
        // Solution: build a temporary User object that "pretends" to be the
        // mocked user. This User is NEVER persisted to the database.
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {

            // Build a minimal User with the mock's username.
            // - email: synthetic (not real, just for tests)
            // - password: placeholder (never validated — auth is handled by @WithMockUser)
            // - role: USER (the mock's roles are enforced by Spring Security separately)
            User tempUser = new User(
                    userDetails.getUsername(),
                    userDetails.getUsername() + "@test.com",
                    "password",
                    UserRole.USER
            );

            // -----------------------------------------------------------------
            // Set a fake ID on the test user
            // -----------------------------------------------------------------
            // In production, JPA assigns the ID when the entity is persisted.
            // In tests, there's no database, so we inject a fake ID via reflection.
            //
            // Why reflection? The 'id' field has no public setter (intentional —
            // IDs should only be assigned by JPA). Reflection is the standard
            // testing workaround.
            //
            // The try-catch is a safety net: if reflection fails for any reason,
            // the ID stays null and tests that depend on it will fail with a
            // NullPointerException, which is easy to debug.

            // Set a fake ID via reflection (test code path only)
            // SonarQube java:S3011 — reflection is acceptable here because:
            //   1. This code only runs during tests (@WithMockUser).
            //   2. The 'id' field has no public setter by design (JPA assigns IDs).
            //   3. The alternative (adding a public setId) would break encapsulation.
            try {
                var idField = User.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(tempUser, 1L);
            } catch (Exception ignored) {
                // Reflection failed — ID stays null.
                // Tests depending on a non-null ID will fail safely with NPE.
            }
            return tempUser;
        }

        // ---------------------------------------------------------------------
        // PATH C: Unknown principal type — safety net
        // ---------------------------------------------------------------------
        // This should never happen in normal operation. If it does, returning
        // null is safer than throwing an exception — the controller can check
        // for a null user and handle it gracefully (e.g., return 401).
        return null;
    }
}