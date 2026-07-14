/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.service;

import io.github.lussac7.taskmanager.domain.AuditAction;
import io.github.lussac7.taskmanager.domain.AuditEntry;
import io.github.lussac7.taskmanager.domain.User;
import io.github.lussac7.taskmanager.domain.UserRole;
import io.github.lussac7.taskmanager.repository.AuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AuditService}. Mocks the repository, tests the real service.
 *
 * <h2>Mocking Strategy</h2>
 * <p>We mock AuditRepository because we don't want to write to a real database
 * in a unit test. Instead, we verify that the service called save() with the
 * correct AuditEntry. The real service logic (creating the AuditEntry with
 * the correct fields) is fully tested.</p>
 *
 * <h2>What is ArgumentCaptor?</h2>
 * <p>An ArgumentCaptor "captures" the argument passed to a mock method so you
 * can inspect it after the call. Instead of just verifying that save() was
 * called, we capture WHAT was passed and assert on its individual fields.
 * This is more thorough than simple verification.</p>
 *
 * <pre>{@code
 * // Simple verification (weak):
 * verify(auditRepository).save(any(AuditEntry.class));
 *
 * // ArgumentCaptor verification (strong):
 * ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
 * verify(auditRepository).save(captor.capture());
 * AuditEntry saved = captor.getValue();
 * assertThat(saved.getAction()).isEqualTo(AuditAction.COMPLETED);
 * }</pre>
 *
 * @see AuditService
 * @see AuditEntry
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService Unit Tests")
class AuditServiceTest {

    // =========================================================================
    // MOCK DEPENDENCIES
    // =========================================================================

    /**
     * @Mock creates a fake AuditRepository. We program it to do nothing
     * (the default for void methods) and verify it was called correctly.
     */
    @Mock
    private AuditRepository auditRepository;

    /**
     * @InjectMocks creates the REAL AuditService and injects the @Mock
     * repository into it. This is the object we're actually testing.
     */
    @InjectMocks
    private AuditService auditService;

    // =========================================================================
    // TEST FIXTURE
    // =========================================================================

    /** A sample user representing whoever performed an action. */
    private User actor;

    @BeforeEach
    void setUp() {
        actor = new User("system", "system@example.com", "pass", UserRole.ADMIN);
    }

    // =========================================================================
    // LOG EVENT TESTS
    // =========================================================================
    // The service has ONE public method. We test it with all four AuditAction
    // values to ensure each is handled correctly.

    @Nested
    @DisplayName("logEvent() — one method, four action types")
    class LogEvent {

        /**
         * Tests ALL fields of the created AuditEntry.
         * This is the "complete" test — the other three tests focus on just
         * the action type to avoid repetition.
         *
         * <p><b>How ArgumentCaptor works step by step:</b>
         * <ol>
         *   <li>Create a captor: {@code ArgumentCaptor.forClass(AuditEntry.class)}</li>
         *   <li>Verify save() was called AND capture its argument:
         *       {@code verify(auditRepository).save(captor.capture())}</li>
         *   <li>Get the captured object: {@code captor.getValue()}</li>
         *   <li>Assert on its fields as normal</li>
         * </ol></p>
         */
        @Test
        @DisplayName("Should create and save an AuditEntry with correct data")
        void shouldCreateAndSaveAuditEntry() {
            // WHEN: logging a COMPLETED event
            auditService.logEvent(AuditAction.COMPLETED, actor, "Task", 100L,
                    "Task marked complete by user");

            // THEN: save() should have been called.
            // The ArgumentCaptor captures whatever was passed to save().
            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditRepository).save(captor.capture());

            // Inspect the captured AuditEntry
            AuditEntry saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo(AuditAction.COMPLETED);
            assertThat(saved.getActor()).isEqualTo(actor);
            assertThat(saved.getTargetType()).isEqualTo("Task");
            assertThat(saved.getTargetId()).isEqualTo(100L);
            assertThat(saved.getDetails()).contains("marked complete");
            assertThat(saved.getTimestamp()).isNotNull();  // Auto-generated by constructor
        }

        /**
         * A CREATED event should produce an entry with action=CREATED.
         * Only the action field is checked — the full field check is in the test above.
         */
        @Test
        @DisplayName("Should capture CREATED action")
        void shouldCaptureCreatedAction() {
            auditService.logEvent(AuditAction.CREATED, actor, "Task", 1L, "New task");

            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditRepository).save(captor.capture());

            assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.CREATED);
        }

        /**
         * An ASSIGNED event should produce an entry with action=ASSIGNED.
         */
        @Test
        @DisplayName("Should capture ASSIGNED action")
        void shouldCaptureAssignedAction() {
            auditService.logEvent(AuditAction.ASSIGNED, actor, "Task", 1L, "Assigned to jane");

            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditRepository).save(captor.capture());

            assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.ASSIGNED);
        }

        /**
         * A DELETED event should produce an entry with action=DELETED.
         */
        @Test
        @DisplayName("Should capture DELETED action")
        void shouldCaptureDeletedAction() {
            auditService.logEvent(AuditAction.DELETED, actor, "Task", 1L, "Task removed");

            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditRepository).save(captor.capture());

            assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.DELETED);
        }
    }
}