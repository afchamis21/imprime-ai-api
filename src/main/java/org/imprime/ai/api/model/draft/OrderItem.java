package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "ORDER_ITEM")
public class OrderItem extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_ITEM_ID", nullable = false)
    private Long id;

    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    @Column(name = "PRODUCT_ID")
    private Long productId;

    @Column(name = "MODEL_3D_ID")
    private Long model3dId;

    @Column(name = "ITEM_NAME", nullable = false, length = 180)
    private String itemName;

    @Convert(converter = MaterialType.Converter.class)
    @Column(name = "MATERIAL", length = 30)
    private MaterialType material;

    @Column(name = "COLOR_NAME", length = 60)
    private String colorName;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "UNIT_PRICE", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "LINE_TOTAL", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;
}
