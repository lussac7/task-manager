/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Task Manager application.
 *
 * <p>{@code @SpringBootApplication} is a convenience annotation that combines
 * three essential annotations:
 * <ol>
 *   <li><b>@Configuration</b> — This class can define @Bean methods.</li>
 *   <li><b>@EnableAutoConfiguration</b> — Spring Boot guesses what beans you
 *       need based on the dependencies in pom.xml. If you have spring-boot-starter-web,
 *       it configures Tomcat and Spring MVC automatically.</li>
 *   <li><b>@ComponentScan</b> — Scans the package of this class (and all sub-packages)
 *       for @Component, @Service, @Repository, @Controller, etc., and registers
 *       them as beans. That's why all your classes just work — they're in
 *       sub-packages of com.taskmanager.</li>
 * </ol></p>
 *
 * <h2>What Happens When You Run This</h2>
 * <ol>
 *   <li>Spring Boot reads application.yml and environment variables.</li>
 *   <li>It scans for components (your controllers, services, repositories).</li>
 *   <li>It auto-configures Tomcat, JPA, Security, etc.</li>
 *   <li>DataInitializer runs (if dev profile is active) and creates demo data.</li>
 *   <li>Tomcat starts listening on port 8080.</li>
 *   <li>The application is ready to accept HTTP requests.</li>
 * </ol>
 *
 * <h2>How to Run</h2>
 * <pre>{@code
 * mvn spring-boot:run -Dspring-boot.run.profiles=dev     # Development (H2)
 * java -jar target/task-manager.jar --spring.profiles.active=prod  # Production (PostgreSQL)
 * }</pre>
 *
 * @see SpringApplication
 * @see io.github.lussac7.taskmanager.config.SecurityConfig
 */
@SpringBootApplication
public class TaskManagerApplication {

	/**
	 * The main method — where the JVM starts executing your code.
	 *
	 * <p>This single line does a LOT:
	 * <ol>
	 *   <li>Creates a Spring ApplicationContext (the "container" that holds all beans).</li>
	 *   <li>Scans for components starting from this class's package (com.taskmanager).</li>
	 *   <li>Auto-configures everything based on pom.xml dependencies.</li>
	 *   <li>Starts the embedded Tomcat web server.</li>
	 *   <li>Blocks the main thread until the application is stopped (Ctrl+C).</li>
	 * </ol></p>
	 *
	 * <p>No XML configuration, no web.xml, no manual server setup —
	 * Spring Boot handles all of that based on conventions and auto-configuration.</p>
	 *
	 * @param args command-line arguments passed to the JVM.
	 *             Spring Boot automatically processes standard arguments like:
	 *             --server.port=9090
	 *             --spring.profiles.active=prod
	 */
	public static void main(String[] args) {
		SpringApplication.run(TaskManagerApplication.class, args);
	}
}