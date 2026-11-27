package org.imprime.ai.api.notification.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "email_notification_log")
public class EmailNotificationLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_notification_log_id", nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 128)
    private String eventId;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "template_name", nullable = false, length = 120)
    private String templateName;

    @Column(name = "recipient_email", nullable = false, length = 255)
    private String recipientEmail;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EmailNotificationStatus status = EmailNotificationStatus.QUEUED;

    @Column(name = "provider", length = 40)
    private String provider;

    @Column(name = "provider_message_id", length = 180)
    private String providerMessageId;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (attempts == null) {
            attempts = 0;
        }
        if (status == null) {
            status = EmailNotificationStatus.QUEUED;
        }
    }
}
