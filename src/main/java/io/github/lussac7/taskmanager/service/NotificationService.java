/*
 * Copyright (c) 2026 LUSSAC PRESTES MAIA
 *
 * Released under the MIT License
 * See the LICENSE file in the project root for details.
 */
package io.github.lussac7.taskmanager.service;

import io.github.lussac7.taskmanager.domain.Notification;
import io.github.lussac7.taskmanager.domain.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sends notifications to users about task events.
 *
 * <p>Currently logs to console. The interface is stable — email, push,
 * or SMS delivery can be added later without changing any callers.</p>
 *
 * <p><b>Called conditionally:</b> only when the user has
 * {@code isNotificationEnabled() == true}. The guard (the "if" check)
 * is in {@link TaskService}, not here — this service focuses on HOW to
 * deliver, not WHEN to deliver.</p>
 *
 * <h2>Separation of Concerns</h2>
 * <p>Why split the guard from the delivery?</p>
 * <ul>
 *   <li>TaskService decides POLICY: "Should we notify this user?"</li>
 *   <li>NotificationService handles MECHANISM: "How do we send it?"</li>
 * </ul>
 * <p>If we change HOW notifications are sent (email → push → SMS), we only
 * change THIS class. If we change WHEN notifications are sent (e.g., only
 * during business hours), we only change TaskService.</p>
 *
 * @see Notification
 * @see TaskService
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    // =========================================================================
    // SEND NOTIFICATION
    // =========================================================================
    // This is the ONLY public method. It does one thing: takes a task and
    // "sends" a notification about it. Currently "sending" means logging
    // to the console — but the method signature won't change when we add
    // real delivery channels.

    /**
     * Sends a notification about a task event.
     *
     * <p>The recipient is {@code task.getAssignedTo()} — may be null if the
     * task is unassigned. In that case, the notification is still created
     * and logged, but has no recipient.</p>
     *
     * <p><b>Future enhancement:</b> When email delivery is added, this method
     * would look up the recipient's email and use JavaMailSender to send
     * an HTML email. The method signature stays the same — only the body changes.</p>
     *
     * @param task the task that triggered the notification
     */
    public void sendNotification(Task task) {
        // Build the notification entity.
        // Message format: "Task updated: <title>"
        // The recipient is task.getAssignedTo() — this may be null if the
        // task is unassigned. The Notification entity accepts null for recipient.
        Notification notification = new Notification(
                "Task updated: " + task.getTitle(),
                task,
                task.getAssignedTo()
        );

        // For now, "sending" means logging to the console.
        // In production, this line would be replaced with:
        //   mailSender.send(emailMessage);
        //   pushService.send(pushNotification);
        //   smsService.send(smsMessage);
        log.info("Notification sent: {}", notification.getMessage());
    }
}