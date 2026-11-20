package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.LicenseType;

import java.math.BigDecimal;
import java.time.Instant;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "PURCHASED_MODEL")
public class PurchasedModel extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PURCHASED_MODEL_ID", nullable = false)
    private Long id;

    @Column(name = "BUYER_USER_ID", nullable = false)
    private Long buyerUserId;

    @Column(name = "MODEL_3D_ID", nullable = false)
    private Long model3dId;

    @Column(name = "ORDER_ID")
    private Long orderId;

    @Column(name = "PRICE_PAID", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePaid;

    @Convert(converter = LicenseType.Converter.class)
    @Column(name = "LICENSE", nullable = false, length = 30)
    private LicenseType license;

    @Column(name = "DOWNLOADS_COUNT", nullable = false)
    private Integer downloadsCount;

    @Column(name = "PURCHASED_AT", nullable = false)
    private Instant purchasedAt;

    @Column(name = "LAST_DOWNLOADED_AT")
    private Instant lastDownloadedAt;
}
