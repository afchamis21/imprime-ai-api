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

@Getter
@Setter
@Entity
@Table(name = "ORDER_FILE_ASSET")
public class OrderFileAsset extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ORDER_FILE_ASSET_ID", nullable = false)
    private Long id;

    @Column(name = "ORDER_ID", nullable = false)
    private Long orderId;

    @Column(name = "FILE_ASSET_ID", nullable = false)
    private Long fileAssetId;

    @Column(name = "PLATE_NUMBER", nullable = false)
    private Integer plateNumber = 1;

    @Column(name = "PLATE_NAME", length = 180)
    private String plateName;

    @Column(name = "MATERIAL", length = 60)
    private String material;

    @Column(name = "COLOR_HEX", length = 30)
    private String colorHex;

    @Column(name = "INFILL_PERCENT")
    private Integer infillPercent;

    @Column(name = "SUPPORTS")
    private Boolean supports;

    @Column(name = "SCALE_X", nullable = false)
    private Double scaleX = 1.0;

    @Column(name = "SCALE_Y", nullable = false)
    private Double scaleY = 1.0;

    @Column(name = "SCALE_Z", nullable = false)
    private Double scaleZ = 1.0;

    @Column(name = "DIMENSION_X_MM", precision = 10, scale = 2)
    private BigDecimal dimensionXMm;

    @Column(name = "DIMENSION_Y_MM", precision = 10, scale = 2)
    private BigDecimal dimensionYMm;

    @Column(name = "DIMENSION_Z_MM", precision = 10, scale = 2)
    private BigDecimal dimensionZMm;

    @Column(name = "COLOR_COUNT", nullable = false)
    private Integer colorCount = 1;

    @Column(name = "LAYER_HEIGHT_MM", precision = 5, scale = 3)
    private BigDecimal layerHeightMm;

    @Column(name = "URGENCY", length = 20)
    private String urgency;

    @Column(name = "IS_MULTI_COLOR", nullable = false)
    private Boolean isMultiColor = Boolean.FALSE;

    @Column(name = "PAINTING_INSTRUCTIONS", columnDefinition = "text")
    private String paintingInstructions;

    @Column(name = "QUANTITY", nullable = false)
    private Integer quantity = 1;

    @Column(name = "WALL_COUNT")
    private Integer wallCount;

    @Column(name = "INFILL_PATTERN", length = 40)
    private String infillPattern;

    @Column(name = "ADHESION_TYPE", length = 40)
    private String adhesionType;

    @Column(name = "SUPPORT_TYPE", length = 40)
    private String supportType;

    @Column(name = "FINISH", length = 40)
    private String finish;

    @Column(name = "TOLERANCE", length = 40)
    private String tolerance;

    @Column(name = "PLATE_NOTES", columnDefinition = "text")
    private String plateNotes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "COLORS_JSON", columnDefinition = "jsonb")
    private String colorsJson;

    @Column(name = "ESTIMATED_WEIGHT_GRAMS", precision = 10, scale = 2)
    private BigDecimal estimatedWeightGrams;

    @Column(name = "ESTIMATED_PRINT_TIME_MINUTES")
    private Integer estimatedPrintTimeMinutes;

    @Column(name = "ESTIMATED_PRICE_BRL", precision = 10, scale = 2)
    private BigDecimal estimatedPriceBrl;
}
