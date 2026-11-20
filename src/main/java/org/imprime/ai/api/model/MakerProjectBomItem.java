package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "MAKER_PROJECT_BOM_ITEM")
public class MakerProjectBomItem extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BOM_ITEM_ID", nullable = false)
    private Long id;

    @Column(name = "MAKER_PROJECT_ID", nullable = false)
    private Long makerProjectId;

    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "QUANTITY", nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "UNIT", length = 30)
    private String unit;

    @Column(name = "UNIT_COST", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "ACQUIRED", nullable = false)
    private Boolean acquired = Boolean.FALSE;
}
