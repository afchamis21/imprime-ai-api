package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.QuoteDepreciationMode;
import org.imprime.ai.api.model.enums.QuotePricingStrategy;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "MAKER_QUOTE_SETTINGS")
public class MakerQuoteSettings extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAKER_QUOTE_SETTINGS_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "DEFAULT_POWER_WATTS", nullable = false)
    private Integer defaultPowerWatts = 300;

    @Convert(converter = QuoteDepreciationMode.Converter.class)
    @Column(name = "DEPRECIATION_MODE", nullable = false, length = 30)
    private QuoteDepreciationMode depreciationMode = QuoteDepreciationMode.FIXED_HOURLY;

    @Column(name = "DEFAULT_MACHINE_PRICE", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultMachinePrice = new BigDecimal("3500.00");

    @Column(name = "DEFAULT_MACHINE_LIFESPAN_HOURS", nullable = false)
    private Integer defaultMachineLifespanHours = 3000;

    @Column(name = "DEFAULT_MAINTENANCE_HOURLY_COST", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultMaintenanceHourlyCost = new BigDecimal("0.50");

    @Column(name = "FAILURE_RATE_PERCENT", nullable = false, precision = 5, scale = 2)
    private BigDecimal failureRatePercent = new BigDecimal("10.00");

    @Column(name = "LABOR_HOURLY_RATE", nullable = false, precision = 10, scale = 2)
    private BigDecimal laborHourlyRate = new BigDecimal("35.00");

    @Column(name = "FIXED_PACKAGING_COST", nullable = false, precision = 10, scale = 2)
    private BigDecimal fixedPackagingCost = new BigDecimal("5.00");

    @Column(name = "DEFAULT_PROFIT_MARGIN_PERCENT", nullable = false, precision = 6, scale = 2)
    private BigDecimal defaultProfitMarginPercent = new BigDecimal("50.00");

    @Column(name = "ADDITIONAL_FEE_PERCENT", nullable = false, precision = 5, scale = 2)
    private BigDecimal additionalFeePercent = BigDecimal.ZERO;

    @Convert(converter = QuotePricingStrategy.Converter.class)
    @Column(name = "PRICING_STRATEGY", nullable = false, length = 30)
    private QuotePricingStrategy pricingStrategy = QuotePricingStrategy.MARKUP;
}
