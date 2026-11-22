package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.FinanceTransactionStatus;
import org.imprime.ai.api.model.enums.FinanceTransactionType;
import org.imprime.ai.api.model.enums.PixKeyType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "MAKER_FINANCE_TRANSACTION")
public class MakerFinanceTransaction extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FINANCE_TRANSACTION_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "ORDER_ID")
    private Long orderId;

    @Column(name = "TRANSACTION_DATE", nullable = false)
    private LocalDate transactionDate;

    @Convert(converter = FinanceTransactionType.Converter.class)
    @Column(name = "TRANSACTION_TYPE", nullable = false, length = 30)
    private FinanceTransactionType transactionType;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "REFERENCE", length = 100)
    private String reference;

    @Column(name = "CATEGORY", length = 80)
    private String category;

    @Column(name = "AMOUNT", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Convert(converter = FinanceTransactionStatus.Converter.class)
    @Column(name = "TRANSACTION_STATUS", nullable = false, length = 20)
    private FinanceTransactionStatus transactionStatus;

    @Convert(converter = PixKeyType.Converter.class)
    @Column(name = "PIX_KEY_TYPE", length = 20)
    private PixKeyType pixKeyType;

    @Column(name = "PIX_KEY_MASKED", length = 140)
    private String pixKeyMasked;

    @Column(name = "IDEMPOTENCY_KEY", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "PROCESSED_BY_USER_ID")
    private Long processedByUserId;

    @Column(name = "PROCESSED_AT")
    private Instant processedAt;

    @Column(name = "FAILURE_REASON", columnDefinition = "text")
    private String failureReason;
}
