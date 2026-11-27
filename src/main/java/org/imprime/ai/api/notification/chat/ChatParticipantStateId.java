package org.imprime.ai.api.notification.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@Embeddable
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipantStateId implements Serializable {
    @Column(name = "chat_id", nullable = false)
    private UUID chatId;

    @Column(name = "user_guid", nullable = false, length = 36)
    private String userGuid;
}
