package org.imprime.ai.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ORDER_SHIPPING_QUOTE")
public class OrderShippingQuote extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_SHIPPING_QUOTE_ID", nullable = false)
    private Long id;

    @Column(name = "BUYER_ID", nullable = false)
    private Long buyerId;

    @Column(name = "VENDOR_ID", nullable = false)
    private Long vendorId;

    @Column(name = "DELIVERY_ADDRESS_ID", nullable = false)
    private Long deliveryAddressId;

    @Column(name = "SERVICE_ID", nullable = false)
    private Integer serviceId;

    @Column(name = "SERVICE_NAME", nullable = false, length = 120)
    private String serviceName;

    @Column(name = "CARRIER_NAME", length = 120)
    private String carrierName;

    @Column(name = "QUOTE_PRICE", nullable = false, precision = 10, scale = 2)
    private BigDecimal quotePrice;

    @Column(name = "DELIVERY_DAYS")
    private Integer deliveryDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "SNAPSHOT", nullable = false, columnDefinition = "jsonb")
    private String snapshotJson;

    @Column(name = "EXPIRES_AT", nullable = false)
    private Instant expiresAt;

    @Column(name = "USED_ORDER_ID")
    private Long usedOrderId;
}
