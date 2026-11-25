package org.imprime.ai.api.config;

import lombok.Data;
import org.imprime.ai.api.model.enums.MaterialType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "imprime.ai.pricing")
public class PrintPricingProperties {
    private BigDecimal basePrice = new BigDecimal("4.00");
    private BigDecimal pricePerGram = new BigDecimal("0.20");
    private BigDecimal pricePerHour = new BigDecimal("2.50");
    private String currency = "BRL";
    private Map<MaterialType, BigDecimal> materialMultipliers = defaultMultipliers();

    private static Map<MaterialType, BigDecimal> defaultMultipliers() {
        Map<MaterialType, BigDecimal> defaults = new EnumMap<>(MaterialType.class);
        defaults.put(MaterialType.PLA, new BigDecimal("1.00"));
        defaults.put(MaterialType.PETG, new BigDecimal("1.15"));
        defaults.put(MaterialType.ABS, new BigDecimal("1.20"));
        defaults.put(MaterialType.ASA, new BigDecimal("1.25"));
        defaults.put(MaterialType.TPU, new BigDecimal("1.35"));
        defaults.put(MaterialType.RESIN, new BigDecimal("1.60"));
        defaults.put(MaterialType.NYLON, new BigDecimal("1.50"));
        defaults.put(MaterialType.CARBON_FIBER, new BigDecimal("1.80"));
        defaults.put(MaterialType.PC, new BigDecimal("1.65"));
        defaults.put(MaterialType.PETG_CF, new BigDecimal("1.55"));
        defaults.put(MaterialType.PA_CF, new BigDecimal("1.90"));
        defaults.put(MaterialType.PVA, new BigDecimal("1.70"));
        defaults.put(MaterialType.HIPS, new BigDecimal("1.25"));
        defaults.put(MaterialType.OTHER, new BigDecimal("1.10"));
        return defaults;
    }
}
