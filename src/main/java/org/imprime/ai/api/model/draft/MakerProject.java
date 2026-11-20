package org.imprime.ai.api.model.draft;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.ProjectStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

// @Entity // Habilitar após aplicar a migration Flyway correspondente
@Getter
@Setter
@Table(name = "MAKER_PROJECT")
public class MakerProject extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MAKER_PROJECT_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "ORDER_ID")
    private Long orderId;

    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "DESCRIPTION", columnDefinition = "text")
    private String description;

    @Convert(converter = ProjectStatus.Converter.class)
    @Column(name = "PROJECT_STATUS", nullable = false, length = 20)
    private ProjectStatus projectStatus;

    @Column(name = "CLIENT_NAME", length = 120)
    private String clientName;

    @Column(name = "DUE_DATE", nullable = false)
    private LocalDate dueDate;

    @Column(name = "TOTAL_PLATES", nullable = false)
    private Integer totalPlates;

    @Column(name = "COMPLETED_PLATES", nullable = false)
    private Integer completedPlates;

    @Column(name = "TOTAL_PARTS", nullable = false)
    private Integer totalParts;

    @Column(name = "COMPLETED_PARTS", nullable = false)
    private Integer completedParts;

    @Column(name = "ESTIMATED_WEIGHT_GRAMS", precision = 10, scale = 2)
    private BigDecimal estimatedWeightGrams;

    @Column(name = "ESTIMATED_HOURS", precision = 8, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "QUOTED_PRICE", nullable = false, precision = 10, scale = 2)
    private BigDecimal quotedPrice;

    @Column(name = "ESTIMATED_COST", precision = 10, scale = 2)
    private BigDecimal estimatedCost;

    @Convert(converter = MaterialType.Converter.class)
    @Column(name = "PRIMARY_MATERIAL", nullable = false, length = 30)
    private MaterialType primaryMaterial;

    @Column(name = "COLOR_HEX", length = 7)
    private String colorHex;

    @Column(name = "THUMBNAIL_URL", length = 1000)
    private String thumbnailUrl;

    @Column(name = "NOTES", columnDefinition = "text")
    private String notes;
}
