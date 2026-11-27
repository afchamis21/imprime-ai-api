package org.imprime.ai.api.notification.preferences;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;

@Getter
@Setter
@Entity
@Table(name = "user_notification_preference")
public class NotificationPreference extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_notification_preference_id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "chat_digest_enabled", nullable = false)
    private Boolean chatDigestEnabled = Boolean.TRUE;

    @Column(name = "reviews_enabled", nullable = false)
    private Boolean reviewsEnabled = Boolean.TRUE;

    @Column(name = "marketing_enabled", nullable = false)
    private Boolean marketingEnabled = Boolean.TRUE;
}
