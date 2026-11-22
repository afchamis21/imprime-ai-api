package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.RefundStatus;
import org.imprime.ai.api.model.enums.RefundType;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "REFUND_REQUEST")
public class RefundRequest extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "REFUND_REQUEST_ID", nullable = false)
    private Long id;

    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    @Column(name = "REQUESTED_BY_USER_ID", nullable = false)
    private Long requestedByUserId;

    @Convert(converter = RefundType.Converter.class)
    @Column(name = "REFUND_TYPE", nullable = false, length = 20)
    private RefundType refundType;

    @Column(name = "REQUESTED_AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "APPROVED_AMOUNT", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Convert(converter = RefundStatus.Converter.class)
    @Column(name = "REFUND_STATUS", nullable = false, length = 20)
    private RefundStatus refundStatus = RefundStatus.PENDING;

    @Column(name = "REASON", nullable = false, columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "EVIDENCE_URLS", columnDefinition = "jsonb")
    private String evidenceUrlsJson = "[]";

    @Column(name = "ADMIN_NOTES", columnDefinition = "text")
    private String adminNotes;

    @Column(name = "REJECTION_REASON", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "STRIPE_REFUND_ID", length = 120)
    private String stripeRefundId;

    @Column(name = "REVIEWED_BY_USER_ID")
    private Long reviewedByUserId;

    @Column(name = "REVIEWED_AT")
    private Instant reviewedAt;
}
