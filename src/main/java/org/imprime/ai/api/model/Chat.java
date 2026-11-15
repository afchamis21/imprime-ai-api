package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.*;
import org.imprime.ai.api.model.base.Auditable;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat")
@EqualsAndHashCode(callSuper = true)
public class Chat extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "chat_id")
    private UUID chatId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;
}
