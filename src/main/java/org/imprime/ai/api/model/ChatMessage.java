package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.*;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.MessageType;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat_message")
@EqualsAndHashCode(callSuper = true)
public class ChatMessage extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "chat_id")
    private UUID chatId;

    @Column(name = "sender_id")
    private String senderId;

    private String content;

    @Convert(converter = MessageType.Converter.class)
    private MessageType type;
}
