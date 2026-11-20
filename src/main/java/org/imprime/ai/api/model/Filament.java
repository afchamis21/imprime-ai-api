package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.MaterialType;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "FILAMENT")
public class Filament extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FILAMENT_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "ASSET_ID")
    private Long assetId;

    @Column(name = "NAME", nullable = false, length = 120)
    private String name;

    @Column(name = "BRAND", nullable = false, length = 80)
    private String brand = "Genérico";

    @Convert(converter = MaterialType.Converter.class)
    @Column(name = "MATERIAL", nullable = false, length = 30)
    private MaterialType material;

    @Column(name = "COLOR_NAME", nullable = false, length = 60)
    private String colorName = "Neutro";

    @Column(name = "COLOR_HEX", nullable = false, length = 7)
    private String colorHex = "#64748b";

    @Column(name = "STOCK", nullable = false, precision = 10, scale = 2)
    private BigDecimal stock = BigDecimal.ZERO;

    @Column(name = "INITIAL_WEIGHT_GRAMS", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialWeightGrams = new BigDecimal("1000.00");

    @Column(name = "COST_PER_KG", nullable = false, precision = 10, scale = 2)
    private BigDecimal costPerKg = BigDecimal.ZERO;

    @Column(name = "DIAMETER_MM", nullable = false, precision = 5, scale = 2)
    private BigDecimal diameterMm = new BigDecimal("1.75");

    @Column(name = "LOW_STOCK_THRESHOLD_GRAMS", nullable = false, precision = 10, scale = 2)
    private BigDecimal lowStockThresholdGrams = new BigDecimal("250.00");

    @Column(name = "HUMIDITY_PERCENT")
    private Integer humidityPercent;

    @Column(name = "SPOOL_LOCATION", length = 100)
    private String spoolLocation;

    @Column(name = "NOTES", length = 500)
    private String notes;

    @Column(name = "FILAMENT_TYPE", nullable = false, length = 100)
    private String filamentType;

    @Column(name = "DESCRIPTION")
    private String description;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version = 0L;
}
