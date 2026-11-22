package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;

@Getter
@Setter
@Entity
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
    private Boolean fromSupport = Boolean.FALSE;

    @Column(name = "MESSAGE", nullable = false, columnDefinition = "text")
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ATTACHMENTS", columnDefinition = "jsonb")
    private String attachmentsJson = "[]";
}
