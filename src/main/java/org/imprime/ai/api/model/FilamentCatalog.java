package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.FilamentFinishType;
import org.imprime.ai.api.model.enums.HygroscopicLevel;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.SpoolType;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "FILAMENT_CATALOG")
public class FilamentCatalog extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FILAMENT_CATALOG_ID", nullable = false)
    private Long id;

    @Column(name = "BRAND", nullable = false, length = 100)
    private String brand;

    @Column(name = "LINE_NAME", nullable = false, length = 120)
    private String lineName;

    @Column(name = "SLUG", nullable = false, unique = true, length = 240)
    private String slug;

    @Convert(converter = MaterialType.Converter.class)
    @Column(name = "MATERIAL", nullable = false, length = 30)
    private MaterialType material;

    @Column(name = "MATERIAL_SUBTYPE", length = 60)
    private String materialSubtype;

    @Column(name = "COLOR_NAME", nullable = false, length = 80)
    private String colorName;

    @Column(name = "COLOR_HEX", nullable = false, length = 7)
    private String colorHex;

    @Column(name = "SECONDARY_COLOR_HEX", length = 7)
    private String secondaryColorHex;

    @Convert(converter = FilamentFinishType.Converter.class)
    @Column(name = "FINISH_TYPE", nullable = false, length = 30)
    private FilamentFinishType finishType = FilamentFinishType.STANDARD;

    @Column(name = "DIAMETER_MM", nullable = false, precision = 4, scale = 2)
    private BigDecimal diameterMm = new BigDecimal("1.75");

    @Column(name = "DENSITY_G_CM3", nullable = false, precision = 5, scale = 3)
    private BigDecimal densityGCm3 = new BigDecimal("1.240");

    @Column(name = "DEFAULT_SPOOL_WEIGHT_G", nullable = false)
    private Integer defaultSpoolWeightG = 1000;

    @Convert(converter = SpoolType.Converter.class)
    @Column(name = "SPOOL_TYPE", length = 30)
    private SpoolType spoolType = SpoolType.PLASTIC;

    @Column(name = "NOZZLE_TEMP_MIN_C", nullable = false)
    private Integer nozzleTempMinC;

    @Column(name = "NOZZLE_TEMP_MAX_C", nullable = false)
    private Integer nozzleTempMaxC;

    @Column(name = "NOZZLE_TEMP_RECOMMENDED_C")
    private Integer nozzleTempRecommendedC;

    @Column(name = "BED_TEMP_MIN_C", nullable = false)
    private Integer bedTempMinC;

    @Column(name = "BED_TEMP_MAX_C", nullable = false)
    private Integer bedTempMaxC;

    @Column(name = "BED_TEMP_RECOMMENDED_C")
    private Integer bedTempRecommendedC;

    @Column(name = "MAX_VOLUMETRIC_SPEED_MM3_S", precision = 5, scale = 2)
    private BigDecimal maxVolumetricSpeedMm3S;

    @Column(name = "PRESSURE_ADVANCE_K", precision = 5, scale = 4)
    private BigDecimal pressureAdvanceK;

    @Column(name = "TRANSMISSION_DISTANCE_MM", precision = 5, scale = 2)
    private BigDecimal transmissionDistanceMm;

    @Column(name = "DRYING_TEMP_C")
    private Integer dryingTempC;

    @Column(name = "DRYING_TIME_HOURS")
    private Integer dryingTimeHours;

    @Column(name = "REQUIRES_ENCLOSURE", nullable = false)
    private Boolean requiresEnclosure = Boolean.FALSE;

    @Column(name = "REQUIRES_HARDENED_NOZZLE", nullable = false)
    private Boolean requiresHardenedNozzle = Boolean.FALSE;

    @Column(name = "AMS_COMPATIBLE", nullable = false)
    private Boolean amsCompatible = Boolean.TRUE;

    @Convert(converter = HygroscopicLevel.Converter.class)
    @Column(name = "HYGROSCOPIC_LEVEL", nullable = false, length = 15)
    private HygroscopicLevel hygroscopicLevel = HygroscopicLevel.LOW;

    @Column(name = "SHORE_HARDNESS", length = 15)
    private String shoreHardness;

    @Column(name = "REFERENCE_PRICE_BRL_PER_KG", precision = 10, scale = 2)
    private BigDecimal referencePriceBrlPerKg;

    @Column(name = "SKU", length = 80)
    private String sku;

    @Column(name = "GTIN_EAN", length = 32)
    private String gtinEan;

    @Column(name = "PURCHASE_URL", length = 500)
    private String purchaseUrl;

    @Column(name = "IMAGE_URL", length = 1000)
    private String imageUrl;

    @Column(name = "NOTES", columnDefinition = "text")
    private String notes;
}
