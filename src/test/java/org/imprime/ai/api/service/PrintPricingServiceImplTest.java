package org.imprime.ai.api.service;

import org.imprime.ai.api.config.PrintPricingProperties;
import org.imprime.ai.api.http.request.print.PrintEstimateRequest;
import org.imprime.ai.api.http.response.print.PrintEstimateResponse;
import org.imprime.ai.api.http.response.print.PrintPricingConfigResponse;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.service.impl.PrintPricingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PrintPricingServiceImplTest {

    private PrintPricingServiceImpl service;

    @BeforeEach
    void setUp() {
        PrintPricingProperties properties = new PrintPricingProperties();
        service = new PrintPricingServiceImpl(properties);
    }

    @Test
    void getPricingConfigReturnsConfiguredDefaults() {
        PrintPricingConfigResponse config = service.getPricingConfig();

        assertEquals(new BigDecimal("4.00"), config.basePrice());
        assertEquals(new BigDecimal("0.20"), config.pricePerGram());
        assertEquals(new BigDecimal("2.50"), config.pricePerHour());
        assertEquals("BRL", config.currency());
        assertEquals(new BigDecimal("1.15"), config.materialMultipliers().get(MaterialType.PETG));
    }

    @Test
    void estimatePrintAppliesMaterialMultiplierAndQuantityWithHalfUpRounding() {
        // weight = 100g * 0.20 * 1.15 (PETG) = 23.00
        // time = 90 min = 1.5h * 2.50 = 3.75
        // unit = 4.00 + 23.00 + 3.75 = 30.75
        // total (qty 2) = 61.50
        PrintEstimateRequest request = new PrintEstimateRequest(
                new BigDecimal("100"),
                90,
                MaterialType.PETG,
                2
        );

        PrintEstimateResponse response = service.estimatePrint(request);

        assertEquals(new BigDecimal("4.00"), response.baseFee());
        assertEquals(new BigDecimal("23.00"), response.materialCost());
        assertEquals(new BigDecimal("3.75"), response.machineTimeCost());
        assertEquals(new BigDecimal("30.75"), response.unitPrice());
        assertEquals(new BigDecimal("61.50"), response.totalPrice());
        assertEquals("BRL", response.currency());
    }

    @Test
    void estimatePrintDefaultsQuantityToOneWhenNull() {
        PrintEstimateRequest request = new PrintEstimateRequest(
                new BigDecimal("50"),
                60,
                MaterialType.PLA,
                null
        );

        PrintEstimateResponse response = service.estimatePrint(request);

        // 4.00 + (50 * 0.20 * 1.00 = 10.00) + (1h * 2.50 = 2.50) = 16.50
        assertEquals(new BigDecimal("16.50"), response.unitPrice());
        assertEquals(new BigDecimal("16.50"), response.totalPrice());
    }
}
