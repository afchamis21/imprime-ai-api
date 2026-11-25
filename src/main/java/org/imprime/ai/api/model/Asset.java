package org.imprime.ai.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.imprime.ai.api.model.base.Auditable;
import org.imprime.ai.api.model.enums.PrinterStatus;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "ASSET")
public class Asset extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ASSET_ID", nullable = false)
    private Long id;

    @Column(name = "COMPANY_ID", nullable = false)
    private Long companyId;

    @Column(name = "PRINTER_CATALOG_ID")
    private Long printerCatalogId;

    @Column(name = "SUPPORTS_MULTICOLOR", nullable = false)
    private Boolean supportsMulticolor = Boolean.FALSE;

    @Column(name = "MULTICOLOR_SLOTS", nullable = false)
    private Integer multicolorSlots = 1;

    @Column(name = "IS_ENCLOSED_OVERRIDE")
    private Boolean isEnclosedOverride;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "SUPPORTED_MATERIALS_OVERRIDE", columnDefinition = "jsonb")
    private String supportedMaterialsOverrideJson;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "MODEL", nullable = false)
    private String model;

    @Column(name = "MAKE", nullable = false)
    private String make;

    @Column(name = "MANUFACTURE_YEAR", nullable = false)
    private Short manufactureYear;

    @Column(name = "TRAY_WIDTH", precision = 10, scale = 2)
    private BigDecimal trayWidth;

    @Column(name = "TRAY_DEPTH", precision = 10, scale = 2)
    private BigDecimal trayDepth;

    @Column(name = "TRAY_HEIGHT", precision = 10, scale = 2)
    private BigDecimal trayHeight;

    @Column(name = "NOZZLE_DIAMETER_MM", nullable = false, precision = 5, scale = 3)
    private BigDecimal nozzleDiameterMm = new BigDecimal("0.400");

    @Column(name = "POWER_WATTS", nullable = false)
    private Integer powerWatts = 300;

    @Column(name = "PURCHASE_PRICE", precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "LIFESPAN_HOURS")
    private Integer lifespanHours;

    @Column(name = "HOURLY_DEPRECIATION_RATE", precision = 10, scale = 2)
    private BigDecimal hourlyDepreciationRate;

    @Column(name = "IP_ADDRESS", length = 60)
    private String ipAddress;

    @Column(name = "SERIAL_NUMBER", length = 100)
    private String serialNumber;

    @Convert(converter = PrinterStatus.Converter.class)
    @Column(name = "PRINTER_STATUS", nullable = false, length = 20)
    private PrinterStatus printerStatus = PrinterStatus.ONLINE;

    @Column(name = "API_KEY_HASH", length = 64)
    private String apiKeyHash;

    @Column(name = "API_KEY_PREFIX", length = 40)
    private String apiKeyPrefix;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "CONFIG", columnDefinition = "jsonb")
    private String configJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "TELEMETRY", columnDefinition = "jsonb")
    private String telemetryJson = "{}";
}
