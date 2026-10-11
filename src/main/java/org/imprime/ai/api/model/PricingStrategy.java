package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;

@Getter
@Setter
@Entity
@Table(name = "PRICING_STRATEGY")
public class PricingStrategy extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRICING_STRATEGY_ID", nullable = false)
    private Long id;

    @Column(name = "VENDOR_ID", nullable = false)
    private Long vendorId;

    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "IS_DEFAULT", nullable = false)
    private Boolean isDefault;
}