package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.SupportTicketCategory;
import org.imprime.ai.api.model.enums.SupportTicketStatus;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "SUPPORT_TICKET")
public class SupportTicket extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SUPPORT_TICKET_ID", nullable = false)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private Long userId;

    @Column(name = "ORDER_ID")
    private Long orderId;

    @Column(name = "PROTOCOL_CODE", nullable = false, unique = true, length = 30)
    private String protocolCode;

    @Column(name = "SUBJECT", nullable = false, length = 150)
    private String subject;

    @Convert(converter = SupportTicketCategory.Converter.class)
    @Column(name = "CATEGORY", nullable = false, length = 30)
    private SupportTicketCategory category;

    @Convert(converter = SupportTicketStatus.Converter.class)
    @Column(name = "TICKET_STATUS", nullable = false, length = 30)
    private SupportTicketStatus ticketStatus;
}
