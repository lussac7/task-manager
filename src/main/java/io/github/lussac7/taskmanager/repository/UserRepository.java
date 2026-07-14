/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.repository;

import io.github.lussac7.taskmanager.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link User} persistence.
 *
 * <p>Standard CRUD methods (findById, save, delete, findAll) are inherited
 * from {@link JpaRepository} — you don't need to write them. Custom query
 * methods use Spring Data derivation — the method name becomes the SQL query.</p>
 *
 * @see User
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // =========================================================================
    // CUSTOM QUERY METHODS
    // =========================================================================
    // These methods go BEYOND what JpaRepository provides out of the box.
    // Spring Data parses the method name and generates the SQL automatically.
    //
    // How query derivation works:
    //
    //   findByUsername(String username)
    //     findBy   → SELECT * FROM users
    //     Username → WHERE username = ?
    //     Returns: Optional<User> (might not exist, never returns null)
    //
    //   existsByUsername(String username)
    //     existsBy → SELECT COUNT(*) > 0 FROM users
    //     Username → WHERE username = ?
    //     Returns: boolean (true if the username is already taken)
    //
    // Spring Data JPA can derive queries from method names using keywords like:
    //   findBy, findAllBy, countBy, existsBy, deleteBy
    //   And, Or, Between, LessThan, GreaterThan, Like, OrderBy, etc.

    /**
     * Finds a user by their unique username.
     * Used by Spring Security during login (UserDetailsServiceImpl).
     *
     * @param username the login name to look up (case-sensitive)
     * @return the user wrapped in Optional, or Optional.empty() if not found.
     *         NEVER returns null — that's the JpaRepository guarantee.
     */
    Optional<User> findByUsername(String username);

    /**
     * Checks if a username already exists in the database.
     * Used during registration to prevent duplicate usernames.
     *
     * @param username the username to check
     * @return true if the username is already taken, false if it's available
     */
    boolean existsByUsername(String username);
}