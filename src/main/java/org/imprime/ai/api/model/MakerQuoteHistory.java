package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.QuoteHistoryStatus;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "MAKER_QUOTE_HISTORY")
public class MakerQuoteHistory extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAKER_QUOTE_HISTORY_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "TITLE", nullable = false, length = 180)
    private String title;

    @Column(name = "CLIENT_NAME", length = 120)
    private String clientName;

    @Convert(converter = QuoteHistoryStatus.Converter.class)
    @Column(name = "QUOTE_STATUS", nullable = false, length = 20)
    private QuoteHistoryStatus quoteStatus = QuoteHistoryStatus.DRAFT;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity = 1;

    @Column(name = "UNIT_PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "FINAL_PRICE_TOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal finalPriceTotal = BigDecimal.ZERO;

    @Column(name = "PRODUCTION_COST_TOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal productionCostTotal = BigDecimal.ZERO;

    @Column(name = "ESTIMATED_PROFIT_TOTAL", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedProfitTotal = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "INPUT_SNAPSHOT", columnDefinition = "jsonb", nullable = false)
    private String inputSnapshotJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "SETTINGS_SNAPSHOT", columnDefinition = "jsonb", nullable = false)
    private String settingsSnapshotJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "RESULT_SNAPSHOT", columnDefinition = "jsonb", nullable = false)
    private String resultSnapshotJson = "{}";
}
