package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "ORDERS")
public class Order extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ID", nullable = false)
    private Long id;

    @Column(name = "BUYER_ID", nullable = false)
    private Long buyerId;

    @Column(name = "VENDOR_ID", nullable = false)
    private Long vendorId;

    @Column(name = "FILE_ASSET_ID", nullable = false)
    private Long fileAssetId;

    @Column(name = "FROM_ADDRESS_ID", nullable = false)
    private Long fromAddressId;

    @Column(name = "TO_ADDRESS_ID", nullable = false)
    private Long toAddressId;

    @Column(name = "PRICING_STRATEGY_ID")
    private Long pricingStrategyId;

    @Column(name = "ORDER_TOTAL")
    private BigDecimal orderTotal;
}