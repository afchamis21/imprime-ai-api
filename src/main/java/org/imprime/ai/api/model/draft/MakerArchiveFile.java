package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;
import java.time.Instant;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "MAKER_ARCHIVE_FILE")
public class MakerArchiveFile extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAKER_ARCHIVE_FILE_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "FILE_ASSET_ID", nullable = false)
    private Long fileAssetId;

    @Column(name = "FILAMENT_ID")
    private Long filamentId;

    @Column(name = "MAKER_PROJECT_ID")
    private Long makerProjectId;

    @Column(name = "FILE_NAME", nullable = false, length = 255)
    private String fileName;

    @Column(name = "DISPLAY_NAME", nullable = false, length = 150)
    private String displayName;

    @Convert(converter = FileFormat3D.Converter.class)
    @Column(name = "FORMAT", nullable = false, length = 20)
    private FileFormat3D format;

    @Column(name = "FILE_SIZE_BYTES", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "THUMBNAIL_URL", length = 1000)
    private String thumbnailUrl;

    @Convert(converter = MaterialType.Converter.class)
    @Column(name = "MATERIAL", nullable = false, length = 30)
    private MaterialType material;

    @Column(name = "COLOR_NAME", length = 60)
    private String colorName;

    @Column(name = "COLOR_HEX", length = 7)
    private String colorHex;

    @Column(name = "ESTIMATED_WEIGHT_GRAMS", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedWeightGrams;

    @Column(name = "ESTIMATED_TIME_MINUTES", nullable = false)
    private Integer estimatedTimeMinutes;

    @Column(name = "LAYER_HEIGHT_MM", precision = 5, scale = 3)
    private BigDecimal layerHeightMm;

    @Column(name = "INFILL_PERCENT")
    private Integer infillPercent;

    @Column(name = "PLATES_COUNT", nullable = false)
    private Integer platesCount;

    @Column(name = "PRINT_COUNT", nullable = false)
    private Integer printCount;

    @Column(name = "LAST_PRINTED_AT")
    private Instant lastPrintedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "TAGS", columnDefinition = "jsonb")
    private String tagsJson;

    @Column(name = "SOURCE_NAME", length = 100)
    private String sourceName;

    @Column(name = "ESTIMATED_COST", precision = 10, scale = 2)
    private BigDecimal estimatedCost;
}
