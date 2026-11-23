package org.imprime.ai.api.controller.maker;

import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteSettingsRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsResponse;
import org.imprime.ai.api.model.enums.QuoteDepreciationMode;
import org.imprime.ai.api.model.enums.QuotePricingStrategy;
import org.imprime.ai.api.service.MakerQuoteSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MakerQuoteSettingsControllerTest {

    @Mock
    private MakerQuoteSettingsService makerQuoteSettingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MakerQuoteSettingsController controller = new MakerQuoteSettingsController(makerQuoteSettingsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getSettingsReturnsCurrentQuoteSettings() throws Exception {
        MakerQuoteSettingsResponse response = new MakerQuoteSettingsResponse(
                new BigDecimal("0.9200"),
                300,
                QuoteDepreciationMode.FIXED_HOURLY,
                new BigDecimal("2.50"),
                new BigDecimal("3500.00"),
                3000,
                new BigDecimal("0.50"),
                new BigDecimal("10.00"),
                new BigDecimal("35.00"),
                new BigDecimal("5.00"),
                new BigDecimal("50.00"),
                new BigDecimal("0.00"),
                QuotePricingStrategy.MARKUP,
                false,
                null
        );
        when(makerQuoteSettingsService.getSettings()).thenReturn(response);

        mockMvc.perform(get("/maker/quote-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kwhRate").value(0.92))
                .andExpect(jsonPath("$.data.defaultPowerWatts").value(300))
                .andExpect(jsonPath("$.data.depreciationMode").value("FIXED_HOURLY"))
                .andExpect(jsonPath("$.data.pricingStrategy").value("MARKUP"))
                .andExpect(jsonPath("$.data.customized").value(false));
    }

    @Test
    void updateSettingsDelegatesValidatedPayloadToService() throws Exception {
        MakerQuoteSettingsResponse response = new MakerQuoteSettingsResponse(
                new BigDecimal("1.1000"),
                350,
                QuoteDepreciationMode.MACHINE_LIFECYCLE,
                new BigDecimal("2.10"),
                new BigDecimal("4200.00"),
                2500,
                new BigDecimal("0.65"),
                new BigDecimal("12.00"),
                new BigDecimal("45.00"),
                new BigDecimal("6.00"),
                new BigDecimal("55.00"),
                new BigDecimal("4.99"),
                QuotePricingStrategy.MARGIN_ON_PRICE,
                true,
                Instant.parse("2026-11-22T20:30:00Z")
        );
        when(makerQuoteSettingsService.updateSettings(any())).thenReturn(response);

        mockMvc.perform(put("/maker/quote-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kwhRate": 1.10,
                                  "defaultPowerWatts": 350,
                                  "depreciationMode": "MACHINE_LIFECYCLE",
                                  "hourlyDepreciationRate": 2.10,
                                  "defaultMachinePrice": 4200.00,
                                  "defaultMachineLifespanHours": 2500,
                                  "defaultMaintenanceHourlyCost": 0.65,
                                  "failureRatePercent": 12.00,
                                  "laborHourlyRate": 45.00,
                                  "fixedPackagingCost": 6.00,
                                  "defaultProfitMarginPercent": 55.00,
                                  "additionalFeePercent": 4.99,
                                  "pricingStrategy": "MARGIN_ON_PRICE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kwhRate").value(1.10))
                .andExpect(jsonPath("$.data.depreciationMode").value("MACHINE_LIFECYCLE"))
                .andExpect(jsonPath("$.data.pricingStrategy").value("MARGIN_ON_PRICE"))
                .andExpect(jsonPath("$.data.customized").value(true));

        ArgumentCaptor<UpdateMakerQuoteSettingsRequest> captor =
                ArgumentCaptor.forClass(UpdateMakerQuoteSettingsRequest.class);
        verify(makerQuoteSettingsService).updateSettings(captor.capture());
        assertEquals(350, captor.getValue().defaultPowerWatts());
        assertEquals(QuoteDepreciationMode.MACHINE_LIFECYCLE, captor.getValue().depreciationMode());
        assertEquals(QuotePricingStrategy.MARGIN_ON_PRICE, captor.getValue().pricingStrategy());
    }
}
