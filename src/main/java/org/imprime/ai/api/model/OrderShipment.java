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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.OrderShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ORDER_SHIPMENT")
public class OrderShipment extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_SHIPMENT_ID", nullable = false)
    private Long id;

    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    @Column(name = "EXTERNAL_ORDER_ID", unique = true, length = 120)
    private String externalOrderId;

    @Column(name = "SERVICE_ID", nullable = false)
    private Integer serviceId;

    @Column(name = "CARRIER_NAME", length = 120)
    private String carrierName;

    @Column(name = "SERVICE_NAME", length = 120)
    private String serviceName;

    @Column(name = "TRACKING_CODE", length = 120)
    private String trackingCode;

    @Column(name = "TRACKING_STATUS", length = 80)
    private String trackingStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "SHIPMENT_STATUS", nullable = false, length = 30)
    private OrderShipmentStatus shipmentStatus = OrderShipmentStatus.SELECTED;

    @Column(name = "PACKAGE_NUMBER", nullable = false)
    private Integer packageNumber = 1;

    @Column(name = "QUOTED_PRICE", precision = 10, scale = 2)
    private BigDecimal quotedPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "PACKAGE_SNAPSHOT", columnDefinition = "jsonb")
    private String packageSnapshotJson;

    @Column(name = "LABEL_URL", columnDefinition = "text")
    private String labelUrl;

    @Column(name = "LABEL_EXPIRES_AT")
    private Instant labelExpiresAt;

    @Column(name = "LAST_ERROR", columnDefinition = "text")
    private String lastError;
}
