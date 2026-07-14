/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration for the Task Manager API.
 *
 * <p>Access the interactive API documentation at:
 * <a href="http://localhost:8080/swagger-ui.html">/swagger-ui.html</a></p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * Creates the OpenAPI bean that Swagger UI uses to generate the
     * interactive documentation page.
     *
     * After starting the app, open http://localhost:8080/swagger-ui.html
     * to see all endpoints, try them out, and read their documentation.
     */
    @Bean
    public OpenAPI taskManagerOpenAPI() {
        return new OpenAPI()
                // --- API Information ---
                // This section populates the header of the Swagger UI page.
                .info(new Info()
                        .title("Task Manager API")
                        .description("REST API for managing tasks with role-based access control")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Lussac P. Maia")
                                .email("lussacmaia@gmail.com")
                                .url("https://github.com/lussac7/task-manager"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                // --- Security Scheme ---
                // Tells Swagger UI: "This API uses HTTP Basic Authentication."
                // When you click the "Authorize" button in Swagger UI, it
                // prompts for username and password, then sends them as a
                // Base64-encoded Authorization header with every request.
                .addSecurityItem(new SecurityRequirement().addList("BasicAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("BasicAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)    // HTTP authentication
                                        .scheme("basic")                   // Basic Auth (not Bearer/JWT)
                                        .name("Basic Authentication")));   // Display name in Swagger UI
    }
}