package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "SUPPORT_TICKET_MESSAGE")
public class SupportTicketMessage extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SUPPORT_TICKET_MESSAGE_ID", nullable = false)
    private Long id;

    @Column(name = "SUPPORT_TICKET_ID", nullable = false)
    private Long supportTicketId;

    @Column(name = "SENDER_USER_ID", nullable = false)
    private Long senderUserId;

    @Column(name = "FROM_SUPPORT", nullable = false)
    private Boolean fromSupport;

    @Column(name = "MESSAGE", nullable = false, columnDefinition = "text")
    private String message;
}
