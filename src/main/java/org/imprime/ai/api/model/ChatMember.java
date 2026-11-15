package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.*;
import org.imprime.ai.api.model.base.Auditable;

import java.io.Serializable;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "chat_member")
@EqualsAndHashCode(callSuper = true)
@IdClass(ChatMember.Identifier.class)
public class ChatMember extends Auditable {

    @Id
    @Column(name = "chat_id")
    private UUID chatId;

    @Id
    @Column(name = "user_id")
    private String userId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Identifier implements Serializable {
        private UUID chatId;
        private String userId;
    }
}