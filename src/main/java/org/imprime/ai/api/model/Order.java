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

    @Column(name = "VENDOR_ID")
    private Long vendorId;

    @Column(name = "FILE_ASSET_ID")
    private Long fileAssetId;

    @Column(name = "FROM_ADDRESS_ID")
    private Long fromAddressId;

    @Column(name = "TO_ADDRESS_ID", nullable = false)
    private Long toAddressId;

    @Column(name = "PRICING_STRATEGY_ID")
    private Long pricingStrategyId;

    @Column(name = "ORDER_TOTAL")
    private BigDecimal orderTotal;

    @Column(name = "ORDER_CODE", length = 30)
    private String orderCode;

    @Column(name = "SHIPPING_COST", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "TRACKING_CODE", length = 100)
    private String trackingCode;

    @Column(name = "NOTES", columnDefinition = "text")
    private String notes;

    @Column(name = "CANCELLATION_REASON", length = 500)
    private String cancellationReason;
}
