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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "CONFIGS", columnDefinition = "jsonb")
    private String configs;

    @Column(name = "EXECUTION_ORDER", nullable = false)
    private Long executionOrder;
}