package org.imprime.ai.api.notification.chat;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "chat_participant_state")
public class ChatParticipantState {
    @EmbeddedId
    private ChatParticipantStateId id;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "last_email_sent_at")
    private Instant lastEmailSentAt;

    @Column(name = "pending_since")
    private Instant pendingSince;

    @Column(name = "next_notify_at")
    private Instant nextNotifyAt;

    @Column(name = "pending_count", nullable = false)
    private int pendingCount = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
