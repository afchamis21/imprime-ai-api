package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.DocumentType;
import org.imprime.ai.api.model.enums.PixKeyType;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "COMPANY")
public class Company extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "COMPANY_ID", nullable = false)
    private Long id;

    @Column(name = "NAME", length = 100)
    private String name;

    @Column(name = "SLUG", nullable = false, unique = true, length = 180)
    private String slug;

    @Column(name = "HANDLE", length = 60)
    private String handle;

    @Column(name = "TAGLINE", length = 200)
    private String tagline;

    @Column(name = "BIO", columnDefinition = "text")
    private String bio;

    @Column(name = "SPECIALTY", length = 150)
    private String specialty;

    @Column(name = "SPECIALTY_CATEGORY", length = 80)
    private String specialtyCategory;

    @Column(name = "PRIMARY_PRINTER", length = 120)
    private String primaryPrinter;

    @Column(name = "BASE_PRICE", precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "MIN_LEAD_TIME_DAYS", nullable = false)
    private Integer minLeadTimeDays = 2;

    @Column(name = "MAX_LEAD_TIME_DAYS", nullable = false)
    private Integer maxLeadTimeDays = 5;

    @Column(name = "RESPONSE_TIME_HOURS", nullable = false)
    private Integer responseTimeHours = 24;

    @Column(name = "MIN_ORDER_VALUE", nullable = false, precision = 10, scale = 2)
    private BigDecimal minOrderValue = BigDecimal.ZERO;

    @Column(name = "AVATAR_URL", length = 1000)
    private String avatarUrl;

    @Column(name = "COVER_URL", length = 1000)
    private String coverUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "CAPABILITIES", columnDefinition = "jsonb")
    private String capabilitiesJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "MATERIALS", columnDefinition = "jsonb")
    private String materialsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "TAGS", columnDefinition = "jsonb")
    private String tagsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "SHIPPING_OPTIONS", columnDefinition = "jsonb")
    private String shippingOptionsJson = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "SOCIAL_LINKS", columnDefinition = "jsonb")
    private String socialLinksJson = "{}";

    @Column(name = "BADGE", length = 60)
    private String badge;

    @Column(name = "ONLINE", nullable = false)
    private Boolean online = Boolean.FALSE;

    @Column(name = "VERIFIED", nullable = false)
    private Boolean verified = Boolean.FALSE;

    @Column(name = "VACATION_MODE", nullable = false)
    private Boolean vacationMode = Boolean.FALSE;

    @Column(name = "VACATION_MESSAGE", length = 500)
    private String vacationMessage;

    @Column(name = "ACCEPTING_CUSTOM_QUOTES", nullable = false)
    private Boolean acceptingCustomQuotes = Boolean.TRUE;

    @Column(name = "PUBLIC_PORTFOLIO_ENABLED", nullable = false)
    private Boolean publicPortfolioEnabled = Boolean.TRUE;

    @Convert(converter = PixKeyType.Converter.class)
    @Column(name = "PIX_KEY_TYPE", length = 20)
    private PixKeyType pixKeyType;

    @Column(name = "PIX_KEY", length = 140)
    private String pixKey;

    @Column(name = "KWH_RATE", precision = 10, scale = 4)
    private BigDecimal kwhRate;

    @Column(name = "HOURLY_DEPRECIATION_RATE", precision = 10, scale = 2)
    private BigDecimal hourlyDepreciationRate;

    @Column(name = "RATING_AVG", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "REVIEWS_COUNT", nullable = false)
    private Integer reviewsCount = 0;

    @Column(name = "PRINTS_DONE", nullable = false)
    private Integer printsDone = 0;

    @Column(name = "DOCUMENT", length = 20)
    private String document;

    @Column(name = "DOCUMENT_TYPE", nullable = false, length = 10)
    @Convert(converter = DocumentType.Converter.class)
    private DocumentType documentType;

    @Column(name = "ADDRESS_ID")
    private Long addressId;

    @Column(name = "OWNER_ID")
    private Long ownerId;
}
