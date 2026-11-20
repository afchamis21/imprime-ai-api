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
@Table(name = "PRODUCT")
public class Product extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "SLUG", nullable = false, unique = true, length = 180)
    private String slug;

    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "CATEGORY", nullable = false, length = 80)
    private String category;

    @Column(name = "PRICE", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "OLD_PRICE", precision = 10, scale = 2)
    private BigDecimal oldPrice;

    @Convert(converter = MaterialType.Converter.class)
    @Column(name = "MATERIAL", nullable = false, length = 30)
    private MaterialType material;

    @Column(name = "COLOR_HEX", length = 7)
    private String colorHex;

    @Column(name = "COVER_IMAGE_URL", length = 1000)
    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "IMAGE_URLS", columnDefinition = "jsonb")
    private String imageUrlsJson = "[]";

    @Column(name = "DESCRIPTION", columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "FEATURES", columnDefinition = "jsonb")
    private String featuresJson = "[]";

    @Column(name = "WIDTH_MM", precision = 8, scale = 2)
    private BigDecimal widthMm;

    @Column(name = "DEPTH_MM", precision = 8, scale = 2)
    private BigDecimal depthMm;

    @Column(name = "HEIGHT_MM", precision = 8, scale = 2)
    private BigDecimal heightMm;

    @Column(name = "WEIGHT_GRAMS")
    private Integer weightGrams;

    @Column(name = "IN_STOCK", nullable = false)
    private Boolean inStock = Boolean.TRUE;

    @Column(name = "PRODUCTION_DAYS", nullable = false)
    private Integer productionDays = 3;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "CUSTOMIZABLE_COLORS", columnDefinition = "jsonb")
    private String customizableColorsJson = "[]";

    @Column(name = "RATING_AVG", precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "REVIEWS_COUNT", nullable = false)
    private Integer reviewsCount = 0;

    @Column(name = "SALES_COUNT", nullable = false)
    private Integer salesCount = 0;
}
