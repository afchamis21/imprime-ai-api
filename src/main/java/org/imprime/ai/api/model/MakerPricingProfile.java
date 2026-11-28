package org.imprime.ai.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.SetupFeeScope;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "MAKER_PRICING_PROFILE")
public class MakerPricingProfile extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAKER_PRICING_PROFILE_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "INSTANT_QUOTE_ENABLED", nullable = false)
    private Boolean instantQuoteEnabled = Boolean.TRUE;

    @Column(name = "HOURLY_RATE_BRL", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRateBrl = new BigDecimal("4.50");

    @Column(name = "MINIMUM_ORDER_FEE_BRL", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumOrderFeeBrl = new BigDecimal("15.00");

    @Column(name = "SETUP_FEE_BRL", nullable = false, precision = 10, scale = 2)
    private BigDecimal setupFeeBrl = new BigDecimal("5.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "SETUP_FEE_SCOPE", nullable = false, length = 20)
    private SetupFeeScope setupFeeScope = SetupFeeScope.PER_PLATE;

    @Column(name = "SUPPORT_REMOVAL_FEE_BRL", nullable = false, precision = 10, scale = 2)
    private BigDecimal supportRemovalFeeBrl = BigDecimal.ZERO;

    @Column(name = "SUPPORT_PRICE_PER_GRAM_BRL", nullable = false, precision = 10, scale = 4)
    private BigDecimal supportPricePerGramBrl = BigDecimal.ZERO;

    @Column(name = "LEAD_TIME_DAYS", nullable = false)
    private Integer leadTimeDays = 3;

    @Column(name = "EXPRESS_MULTIPLIER", nullable = false, precision = 4, scale = 2)
    private BigDecimal expressMultiplier = new BigDecimal("1.20");

    @Column(name = "FINISH_SANDED_MULTIPLIER", nullable = false, precision = 4, scale = 2)
    private BigDecimal finishSandedMultiplier = new BigDecimal("1.15");

    @Column(name = "FINISH_VAPOR_MULTIPLIER", nullable = false, precision = 4, scale = 2)
    private BigDecimal finishVaporMultiplier = new BigDecimal("1.25");

    @Column(name = "FINISH_PAINTED_MULTIPLIER", nullable = false, precision = 4, scale = 2)
    private BigDecimal finishPaintedMultiplier = new BigDecimal("1.35");

    public static MakerPricingProfile defaultForCompany(Long companyId) {
        MakerPricingProfile profile = new MakerPricingProfile();
        profile.setCompanyId(companyId);
        return profile;
    }
}
