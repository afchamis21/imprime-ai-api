package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.imprime.ai.api.model.base.Auditable;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "PRICING_RULE")
public class PricingRule extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRICING_RULE_ID", nullable = false)
    private Long id;

    @Column(name = "PRICING_STRATEGY_ID", nullable = false)
    private Long pricingStrategyId;

    @Column(name = "IMPLEMENTATION_CLASS", nullable = false)
    private String implementationClass;

    @Lob
    @Column(name = "CONFIGS")
    private String configs;

    @Column(name = "EXECUTION_ORDER", nullable = false)
    private Long executionOrder;
}