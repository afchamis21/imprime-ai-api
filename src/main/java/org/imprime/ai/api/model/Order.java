package org.imprime.ai.api.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.enums.OrderPricingSource;
import org.imprime.ai.api.model.enums.PrinterSpeedTier;
import org.imprime.ai.api.model.enums.SlicingValidationStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @Column(name = "SERVICE_AMOUNT", precision = 10, scale = 2)
    private BigDecimal serviceAmount;

    @Column(name = "PRODUCTION_DAYS")
    private Integer productionDays;

    @Column(name = "PACKAGE_WEIGHT", precision = 10, scale = 3)
    private BigDecimal packageWeight;

    @Column(name = "PACKAGE_HEIGHT", precision = 10, scale = 2)
    private BigDecimal packageHeight;

    @Column(name = "PACKAGE_WIDTH", precision = 10, scale = 2)
    private BigDecimal packageWidth;

    @Column(name = "PACKAGE_LENGTH", precision = 10, scale = 2)
    private BigDecimal packageLength;

    @Column(name = "ORDER_CODE", length = 30)
    private String orderCode;

    @Column(name = "SHIPPING_COST", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(name = "TRACKING_CODE", length = 100)
    private String trackingCode;

    @Column(name = "SHIPPING_SERVICE_ID")
    private Integer shippingServiceId;

    @Column(name = "SHIPPING_SERVICE_NAME", length = 120)
    private String shippingServiceName;

    @Column(name = "SHIPPING_QUOTE_PRICE", precision = 10, scale = 2)
    private BigDecimal shippingQuotePrice;

    @Column(name = "SHIPPING_DELIVERY_DAYS")
    private Integer shippingDeliveryDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "SHIPPING_QUOTE_SNAPSHOT", columnDefinition = "jsonb")
    private String shippingQuoteSnapshotJson;

    @Column(name = "EXTERNAL_SHIPMENT_ID", length = 120)
    private String externalShipmentId;

    @Column(name = "CARRIER_NAME", length = 120)
    private String carrierName;

    @Column(name = "TRACKING_STATUS", length = 80)
    private String trackingStatus;

    @Column(name = "TRACKING_URL", columnDefinition = "text")
    private String trackingUrl;

    @Column(name = "NOTES", columnDefinition = "text")
    private String notes;

    @Column(name = "CANCELLATION_REASON", length = 500)
    private String cancellationReason;

    @Column(name = "CHECKOUT_SESSION_ID", length = 120)
    private String checkoutSessionId;

    @Column(name = "PAYMENT_CONFIRMED_AT")
    private java.time.Instant paymentConfirmedAt;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "PRICING_SOURCE", nullable = false, length = 30)
    private OrderPricingSource pricingSource = OrderPricingSource.CHAT_OFFER;

    @Column(name = "SELECTED_PRINTER_ASSET_ID")
    private Long selectedPrinterAssetId;

    @Column(name = "SELECTED_PRINTER_NAME", length = 120)
    private String selectedPrinterName;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "SELECTED_SPEED_TIER", length = 20)
    private PrinterSpeedTier selectedSpeedTier;

    @Column(name = "SELECTED_SPEED_MULTIPLIER", precision = 4, scale = 2)
    private BigDecimal selectedSpeedMultiplier;

    @Column(name = "ESTIMATED_PRINT_TIME_SECONDS")
    private Integer estimatedPrintTimeSeconds;

    @Column(name = "CLIENT_QUOTED_AMOUNT", precision = 10, scale = 2)
    private BigDecimal clientQuotedAmount;

    @Column(name = "SERVER_VALIDATED_AMOUNT", precision = 10, scale = 2)
    private BigDecimal serverValidatedAmount;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "SLICING_VALIDATION_STATUS", length = 30)
    private SlicingValidationStatus slicingValidationStatus;

    @Column(name = "SLICING_VALIDATED_AT")
    private java.time.Instant slicingValidatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "SLICING_VALIDATION_SUMMARY", columnDefinition = "jsonb")
    private String slicingValidationSummaryJson;
}
