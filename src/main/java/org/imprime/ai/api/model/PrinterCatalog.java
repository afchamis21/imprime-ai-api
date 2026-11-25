package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.ExtruderType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrinterKinematics;
import org.imprime.ai.api.model.enums.PrinterTechnology;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "PRINTER_CATALOG")
public class PrinterCatalog extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRINTER_CATALOG_ID", nullable = false)
    private Long id;

    @Column(name = "BRAND", nullable = false, length = 100)
    private String brand;

    @Column(name = "MODEL_NAME", nullable = false, length = 150)
    private String modelName;

    @Column(name = "SLUG", nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "BUILD_VOLUME_X_MM", nullable = false, precision = 8, scale = 2)
    private BigDecimal buildVolumeXMm;

    @Column(name = "BUILD_VOLUME_Y_MM", nullable = false, precision = 8, scale = 2)
    private BigDecimal buildVolumeYMm;

    @Column(name = "BUILD_VOLUME_Z_MM", nullable = false, precision = 8, scale = 2)
    private BigDecimal buildVolumeZMm;

    @Column(name = "IS_ENCLOSED", nullable = false)
    private Boolean isEnclosed = Boolean.FALSE;

    @Column(name = "MAX_NOZZLE_TEMP_C", nullable = false)
    private Integer maxNozzleTempC;

    @Column(name = "MAX_BED_TEMP_C", nullable = false)
    private Integer maxBedTempC;

    @Column(name = "DEFAULT_NOZZLE_MM", nullable = false, precision = 5, scale = 3)
    private BigDecimal defaultNozzleMm = new BigDecimal("0.400");

    @Column(name = "FILAMENT_DIAMETER_MM", precision = 4, scale = 2)
    private BigDecimal filamentDiameterMm = new BigDecimal("1.75");

    @Column(name = "MAX_PRINT_SPEED_MM_S")
    private Integer maxPrintSpeedMmS;

    @Column(name = "MAX_POWER_WATTS_110V")
    private Integer maxPowerWatts110v;

    @Column(name = "MAX_POWER_WATTS_220V")
    private Integer maxPowerWatts220v;

    @Column(name = "INPUT_VOLTAGE", nullable = false, length = 40)
    private String inputVoltage = "BIVOLT_110_220V";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "CONNECTIVITY_METHODS", nullable = false, columnDefinition = "jsonb")
    private String connectivityMethodsJson = "[]";

    @Column(name = "WEIGHT_KG", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Convert(converter = PrinterTechnology.Converter.class)
    @Column(name = "TECHNOLOGY", nullable = false, length = 20)
    private PrinterTechnology technology = PrinterTechnology.FDM;

    @Convert(converter = PrinterKinematics.Converter.class)
    @Column(name = "KINEMATICS", length = 30)
    private PrinterKinematics kinematics;

    @Convert(converter = ExtruderType.Converter.class)
    @Column(name = "EXTRUDER_TYPE", length = 30)
    private ExtruderType extruderType;

    @Column(name = "SUPPORTS_MULTICOLOR", nullable = false)
    private Boolean supportsMulticolor = Boolean.FALSE;

    @Column(name = "MULTICOLOR_SYSTEM_NAME", length = 80)
    private String multicolorSystemName;

    @Column(name = "MAX_COLOR_SLOTS", nullable = false)
    private Integer maxColorSlots = 1;

    @Column(name = "AVG_POWER_WATTS")
    private Integer avgPowerWatts;

    @Column(name = "ACTIVE_CHAMBER_HEATING", nullable = false)
    private Boolean activeChamberHeating = Boolean.FALSE;

    @Column(name = "MAX_CHAMBER_TEMP_C")
    private Integer maxChamberTempC;

    @Column(name = "MIN_LAYER_HEIGHT_MM", precision = 5, scale = 3)
    private BigDecimal minLayerHeightMm;

    @Column(name = "MAX_LAYER_HEIGHT_MM", precision = 5, scale = 3)
    private BigDecimal maxLayerHeightMm;

    @Column(name = "MAX_ACCELERATION_MM_S2")
    private Integer maxAccelerationMmS2;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "SUPPORTED_NOZZLE_SIZES_MM", nullable = false, columnDefinition = "jsonb")
    private String supportedNozzleSizesJson = "[0.4]";

    @Column(name = "HARDENED_NOZZLE_DEFAULT", nullable = false)
    private Boolean hardenedNozzleDefault = Boolean.FALSE;

    @Column(name = "HAS_AUTO_BED_LEVELING", nullable = false)
    private Boolean hasAutoBedLeveling = Boolean.TRUE;

    @Column(name = "HAS_FILAMENT_RUNOUT_SENSOR", nullable = false)
    private Boolean hasFilamentRunoutSensor = Boolean.TRUE;

    @Column(name = "HAS_CAMERA", nullable = false)
    private Boolean hasCamera = Boolean.FALSE;

    @Column(name = "HAS_AIR_FILTRATION", nullable = false)
    private Boolean hasAirFiltration = Boolean.FALSE;

    @Column(name = "RELEASE_YEAR")
    private Integer releaseYear;

    @Column(name = "ESTIMATED_PRICE_BRL", precision = 12, scale = 2)
    private BigDecimal estimatedPriceBrl;

    @Column(name = "IMAGE_URL", length = 1000)
    private String imageUrl;

    @Column(name = "OFFICIAL_SPECS_URL", length = 500)
    private String officialSpecsUrl;

    @Column(name = "NOTES", columnDefinition = "text")
    private String notes;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "PRINTER_CATALOG_MATERIAL",
            joinColumns = @JoinColumn(name = "PRINTER_CATALOG_ID")
    )
    @Column(name = "MATERIAL", nullable = false, length = 30)
    @Convert(converter = MaterialType.Converter.class)
    private Set<MaterialType> supportedMaterials = new LinkedHashSet<>();

    public String getFullDisplayName() {
        String b = brand != null ? brand.trim() : "";
        String m = modelName != null ? modelName.trim() : "";
        if (b.isEmpty()) {
            return m;
        }
        if (m.isEmpty()) {
            return b;
        }
        return m.toLowerCase().startsWith(b.toLowerCase()) ? m : (b + " " + m);
    }
}
