/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.config;

import io.github.lussac7.taskmanager.domain.User;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registers our custom {@link CurrentUserArgumentResolver} with Spring MVC.
 *
 * <p>Without this registration, {@code @AuthenticationPrincipal User} parameters
 * would receive Spring Security's default {@code UserDetails} instead of our
 * domain {@link User} entity.</p>
 *
 * @see CurrentUserArgumentResolver
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // =========================================================================
    // The custom resolver we want to register.
    // It knows how to extract our domain User from the SecurityContext.
    // =========================================================================
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    /**
     * @param currentUserArgumentResolver the resolver that extracts our domain User
     */
    public WebMvcConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================
    // This method is called ONCE when the application starts.
    // We add our resolver to the end of the list, so built-in resolvers
    // (for @PathVariable, @RequestParam, etc.) run first.
    // Our resolver only fires for types that no one else claims — perfect.

    /**
     * Adds our resolver to Spring MVC's chain. Called once at startup.
     *
     * @param resolvers the mutable list of all registered argument resolvers
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // Add our resolver to the END of the list.
        // Built-in resolvers (for @PathVariable, @RequestParam, etc.) run first.
        // Our resolver only claims User.class, so it won't interfere with them.
        resolvers.add(currentUserArgumentResolver);
    }
}