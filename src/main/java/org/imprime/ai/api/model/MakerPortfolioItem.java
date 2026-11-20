package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "MAKER_PORTFOLIO_ITEM")
public class MakerPortfolioItem extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PORTFOLIO_ITEM_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "TITLE", nullable = false, length = 150)
    private String title;

    @Convert(converter = MaterialType.Converter.class)
    @Column(name = "MATERIAL", nullable = false, length = 30)
    private MaterialType material;

    @Column(name = "COVER_IMAGE_URL", length = 1000)
    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "IMAGE_URLS", columnDefinition = "jsonb")
    private String imageUrlsJson = "[]";

    @Column(name = "DESCRIPTION", columnDefinition = "text")
    private String description;

    @Column(name = "PRINT_TIME_MINUTES")
    private Integer printTimeMinutes;

    @Column(name = "PRINTER_USED", length = 120)
    private String printerUsed;

    @Column(name = "LAYER_HEIGHT_MM", precision = 5, scale = 3)
    private BigDecimal layerHeightMm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "TAGS", columnDefinition = "jsonb")
    private String tagsJson = "[]";
}
