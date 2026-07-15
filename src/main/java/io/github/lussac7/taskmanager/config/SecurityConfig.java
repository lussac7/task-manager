/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.config;

import io.github.lussac7.taskmanager.domain.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security configuration for the Task Manager.
 *
 * <h2>Key Behaviors</h2>
 * <ul>
 *   <li><b>Browser pages:</b> Form login at {@code /login}, redirect to the SPA frontend on success.</li>
 *   <li><b>REST API:</b> Returns 401 JSON (not a redirect) for unauthenticated requests.</li>
 *   <li><b>CSRF:</b> Disabled for {@code /api/**}, {@code /login}, {@code /logout}.</li>
 *   <li><b>Role hierarchy:</b> {@code ROLE_ADMIN > ROLE_USER} - admins inherit all user permissions.</li>
 * </ul>
 *
 * @see UserRole
 */
@Configuration
@EnableWebSecurity  // Turns on Spring Security's web security features
public class SecurityConfig {

    // =========================================================================
    // The URL where the frontend SPA is running.
    // In development: http://localhost:3000 (Vite dev server).
    // In production: https://app.yourdomain.com (set via FRONTEND_URL env var).
    // =========================================================================
    @Value("${app.frontend-url}")
    private String frontendUrl;

    // =========================================================================
    // SECURITY FILTER CHAIN
    // =========================================================================
    // This is THE most important method in the security configuration.
    // Every HTTP request passes through this chain of filters in order.
    // Think of it like airport security:
    //   CSRF check → Authorization check → Exception handling → Login → Logout

    /**
     * Builds the security filter chain that processes every HTTP request.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // =============================================================
                // CSRF PROTECTION (Cross-Site Request Forgery)
                // =============================================================
                // CSRF attacks trick a user's browser into submitting unwanted
                // requests. Spring Security blocks POST/PATCH/DELETE without a
                // valid CSRF token.
                //
                // We DISABLE CSRF for:
                //   /api/**     - REST API uses Authorization header, not cookies
                //   /h2-console - Development database browser
                //   /login      - The login form must work before authentication
                //   /logout     - Same reason
                //   /api/users/register    - Public registration endpoint
                //   /error                 - error pages shouldn't need CSRF
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/api/**",
                                "/h2-console/**",
                                "/login",
                                "/logout",
                                "/api/users/register",
                                "/error"
                        )
                )

                // =============================================================
                // AUTHORIZATION RULES (Who can access what)
                // =============================================================
                // RULES ARE EVALUATED IN ORDER - FIRST MATCH WINS!
                // More specific patterns must come BEFORE general ones.
                //
                // Each rule says: "For requests matching this URL pattern,
                // the user must have this role (or higher, via hierarchy)."
                // =============================================================
                .authorizeHttpRequests(auth -> auth
                        // --- PUBLIC: No authentication needed ---
                        .requestMatchers("/css/**", "/js/**").permitAll()       // Static files
                        .requestMatchers("/index.html", "/register.html").permitAll()  // SPA pages
                        .requestMatchers("/favicon.ico").permitAll()            // Browser icon
                        .requestMatchers("/api/users/register").permitAll()     // Self-registration API
                        .requestMatchers("/h2-console/**").permitAll()          // Dev database console
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // --- ADMIN ONLY ---
                        .requestMatchers("/admin.html", "/admin-users.html").hasRole(UserRole.ADMIN.name())
                        .requestMatchers("/api/admin/**").hasRole(UserRole.ADMIN.name())
                        .requestMatchers("/api/users/**").hasRole(UserRole.ADMIN.name())

                        // --- USER (and admin, via role hierarchy) ---
                        .requestMatchers("/dashboard.html").hasRole(UserRole.USER.name())
                        .requestMatchers("/api/tasks/**").hasRole(UserRole.USER.name())

                        // --- Everything else requires authentication ---
                        .anyRequest().authenticated()
                )

                // =============================================================
                // EXCEPTION HANDLING: API vs Browser
                // =============================================================
                // When an unauthenticated user tries to access:
                //   /api/**      → Return 401 JSON (so REST clients know to re-authenticate)
                //   /index.html  → Redirect to login page (browser follows redirect)
                // =============================================================
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),  // Send 401 status
                                new AntPathRequestMatcher("/api/**")                // Only for /api/**
                        )
                )

                // =============================================================
                // HTTP BASIC AUTHENTICATION
                // =============================================================
                // Enables authentication via "Authorization: Basic" header.
                // Used by curl, Postman, and the Vite proxy during development.
                // =============================================================
                .httpBasic(httpBasic -> {})

                // =============================================================
                // FORM LOGIN (Browser-based authentication)
                // =============================================================
                // When a user submits the login form (POST /login):
                //   1. Spring Security extracts username + password
                //   2. Calls UserDetailsServiceImpl.loadUserByUsername()
                //   3. Validates password with BCryptPasswordEncoder
                //   4. On success: creates a session, redirects to dashboard
                //   5. On failure: redirects back to login page with ?error
                // =============================================================
                .formLogin(form -> form
                        .loginPage("/index.html")                                 // Our custom login page
                        .loginProcessingUrl("/login")                             // Spring intercepts POST here
                        .defaultSuccessUrl(frontendUrl + "/dashboard.html")       // Redirect on success
                        .failureUrl(frontendUrl + "/index.html?error")  // Redirect on failure
                        .permitAll()                                              // Everyone can see login
                )

                // =============================================================
                // LOGOUT
                // =============================================================
                // POST /logout → destroys the session, deletes the JSESSIONID
                // cookie, and redirects to the login page.
                // =============================================================
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl(frontendUrl + "/index.html?logout")
                        .invalidateHttpSession(true)       // Destroy server-side session
                        .deleteCookies("JSESSIONID")       // Remove client-side cookie
                        .permitAll()
                )

                // =============================================================
                // HEADERS
                // =============================================================
                // Allow the H2 Console to be displayed in an HTML frame.
                // Spring Security blocks frames by default (clickjacking protection).
                // =============================================================
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)  // 1 year
                        )
                        .contentTypeOptions(HeadersConfigurer.ContentTypeOptionsConfig::disable)  //Actually adds nosniff                        // Add these to force HTTPS and prevent clickjacking/MIME sniffing
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; " +
                                "script-src 'self' 'unsafe-inline'; " +
                                "style-src 'self' 'unsafe-inline'")
                        )
                );

        return http.build();  // Build the chain and return it
    }

    // =========================================================================
    // ROLE HIERARCHY
    // =========================================================================
    // ROLE_ADMIN > ROLE_USER means:
    // "If you have ROLE_ADMIN, you AUTOMATICALLY have ROLE_USER too."
    //
    // Without this, an admin couldn't access endpoints that require USER role.
    // This mirrors the Use Case Diagram: Admin --|> User (inheritance).

    /**
     * Role hierarchy: ROLE_ADMIN inherits all ROLE_USER permissions.
     */
    @Bean
    public RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("ROLE_ADMIN > ROLE_USER");
    }

    // =========================================================================
    // PASSWORD ENCODER
    // =========================================================================
    // BCrypt is the industry standard for password hashing.
    // - Built-in salt: each hash is unique, even for identical passwords
    // - Adaptive: can increase work factor as hardware improves
    // - One-way: hashes can never be reversed to reveal the original password

    /**
     * BCrypt password encoder. Used for hashing and verifying passwords.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // =========================================================================
    // AUTHENTICATION MANAGER
    // =========================================================================
    // Exposes the AuthenticationManager as a Spring bean.
    // Needed for programmatic authentication (e.g., in tests).

    /**
     * Exposes the AuthenticationManager for programmatic authentication.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}