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
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "MAKER_MATERIAL_PRICE")
public class MakerMaterialPrice extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAKER_MATERIAL_PRICE_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "MATERIAL", nullable = false, length = 30)
    private MaterialType material;

    @Column(name = "PRICE_PER_GRAM_BRL", nullable = false, precision = 10, scale = 4)
    private BigDecimal pricePerGramBrl;

    @Column(name = "ENABLED", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "NOTES", length = 200)
    private String notes;
}
