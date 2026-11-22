package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.LicenseType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ModelModerationStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "MODEL_3D")
public class Model3D extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MODEL_3D_ID", nullable = false)
    private Long id;

    @Column(name = "AUTHOR_USER_ID", nullable = false)
    private Long authorUserId;

    @Column(name = "FILE_ASSET_ID")
    private Long fileAssetId;

    @Column(name = "SLUG", nullable = false, unique = true, length = 180)
    private String slug;

    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "CATEGORY", nullable = false, length = 80)
    private String category;

    @Column(name = "PRICE", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "DESCRIPTION", columnDefinition = "text")
    private String description;

    @Convert(converter = FileFormat3D.Converter.class)
    @Column(name = "FORMAT", nullable = false, length = 20)
    private FileFormat3D format;

    @Convert(converter = LicenseType.Converter.class)
    @Column(name = "LICENSE", nullable = false, length = 30)
    private LicenseType license;

    @Convert(converter = MaterialType.Converter.class)
    @Column(name = "RECOMMENDED_MATERIAL", length = 30)
    private MaterialType recommendedMaterial;

    @Column(name = "ESTIMATED_WEIGHT_GRAMS")
    private Integer estimatedWeightGrams;

    @Column(name = "WIDTH_MM", precision = 8, scale = 2)
    private BigDecimal widthMm;

    @Column(name = "DEPTH_MM", precision = 8, scale = 2)
    private BigDecimal depthMm;

    @Column(name = "HEIGHT_MM", precision = 8, scale = 2)
    private BigDecimal heightMm;

    @Column(name = "FILE_SIZE_BYTES")
    private Long fileSizeBytes;

    @Column(name = "CUSTOMIZABLE", nullable = false)
    private Boolean customizable = Boolean.FALSE;

    @Column(name = "COVER_IMAGE_URL", length = 1000)
    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "IMAGE_URLS", columnDefinition = "jsonb")
    private String imageUrlsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "TAGS", columnDefinition = "jsonb")
    private String tagsJson = "[]";

    @Column(name = "RATING_AVG", precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "REVIEWS_COUNT", nullable = false)
    private Integer reviewsCount = 0;

    @Column(name = "DOWNLOADS_COUNT", nullable = false)
    private Long downloadsCount = 0L;

    @Column(name = "VIEWS_COUNT", nullable = false)
    private Long viewsCount = 0L;

    @Convert(converter = ModelModerationStatus.Converter.class)
    @Column(name = "MODERATION_STATUS", nullable = false, length = 30)
    private ModelModerationStatus moderationStatus = ModelModerationStatus.APPROVED;

    @Column(name = "MODERATION_REASON", columnDefinition = "text")
    private String moderationReason;

    @Column(name = "DMCA_CLAIMANT", length = 200)
    private String dmcaClaimant;

    @Column(name = "DMCA_REFERENCE", length = 120)
    private String dmcaReference;

    @Column(name = "MODERATED_BY_USER_ID")
    private Long moderatedByUserId;

    @Column(name = "MODERATED_AT")
    private Instant moderatedAt;
}
