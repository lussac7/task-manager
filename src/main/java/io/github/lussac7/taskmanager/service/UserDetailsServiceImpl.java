/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.service;

import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security adapter — loads users from the database during login.
 *
 * <p>Called automatically by Spring Security's authentication filter.
 * Our {@link User} entity implements {@link UserDetails} directly, so no
 * mapping or wrapper is needed — the entity is returned as-is.</p>
 *
 * <h2>How This Fits in the Login Flow</h2>
 * <ol>
 *   <li>User submits login form (username + password).</li>
 *   <li>Spring Security's {@code UsernamePasswordAuthenticationFilter} intercepts the POST.</li>
 *   <li>The filter calls this method: {@code loadUserByUsername("alice")}.</li>
 *   <li>We look up "alice" in the database via UserRepository.</li>
 *   <li>Spring Security takes the returned User and validates the password
 *       (hashes the submitted password, compares to the stored BCrypt hash).</li>
 *   <li>If the password matches → user is authenticated → JSESSIONID cookie is set.</li>
 *   <li>If the password doesn't match → redirect to login page with ?error.</li>
 * </ol>
 *
 * @see User
 * @see io.github.lussac7.taskmanager.config.SecurityConfig
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    // =========================================================================
    // DEPENDENCY
    // =========================================================================
    // Only one dependency — the repository that finds users by username.
    // This is the "Adapter" pattern: we adapt our UserRepository to the
    // UserDetailsService interface that Spring Security expects.

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // =========================================================================
    // LOAD USER BY USERNAME
    // =========================================================================
    // This is the ONE method required by the UserDetailsService interface.
    // Spring Security calls it on EVERY login attempt.
    //
    // Contract:
    // - If user found → return the UserDetails (our User entity)
    // - If user NOT found → throw UsernameNotFoundException (NEVER return null)
    //
    // Why does our User entity work as UserDetails?
    // Because User implements the UserDetails interface! This means:
    //   getUsername()  → returns the login name
    //   getPassword()  → returns the BCrypt hash
    //   getAuthorities() → returns ROLE_USER or ROLE_ADMIN
    //   isEnabled()    → returns true
    // No mapping, no wrapping, no adapter class needed.

    /**
     * Loads a user by username. Called by Spring Security on every login attempt.
     *
     * <p>{@code @Transactional(readOnly = true)} is an optimization: it tells
     * Hibernate to skip dirty checking (tracking changes to the loaded entity).
     * Since authentication only reads data, this saves memory and CPU.</p>
     *
     * @param username the login name entered in the form (case-sensitive)
     * @return the User entity loaded from the database
     * @throws UsernameNotFoundException if no user exists with that username.
     *         Spring Security catches this and treats it as a failed login.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Delegate to the repository. Spring Data JPA generates:
        // SELECT * FROM users WHERE username = ?
        //
        // If found: the User entity is returned as UserDetails (polymorphism!)
        // If not found: throw UsernameNotFoundException (the interface contract)
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}