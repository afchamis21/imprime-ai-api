package org.imprime.ai.api.controller.maker;

import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteModulesRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteModuleConfigResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsBundleResponse;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.service.MakerQuoteSettingsService;
import org.imprime.ai.api.util.JsonCodec;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private JsonCodec jsonCodec;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonCodec();
        MakerQuoteSettingsController controller = new MakerQuoteSettingsController(makerQuoteSettingsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getSettingsReturnsModularBundle() throws Exception {
        MakerQuoteSettingsBundleResponse response = new MakerQuoteSettingsBundleResponse(
                false,
                null,
                new MakerQuoteSettingsBundleResponse.CompanyRatesSnapshot(
                        new BigDecimal("0.9200"),
                        new BigDecimal("2.50"),
                        new BigDecimal("20.00")
                ),
                List.of(
                        new MakerQuoteModuleConfigResponse(
                                "POWER",
                                "Energia Elétrica",
                                "Consumo elétrico",
                                QuoteModuleType.BASE_COST,
                                10,
                                20,
                                true,
                                true,
                                true,
                                null,
                                jsonCodec.readTree("{\"kwhRate\":0.92,\"defaultPowerWatts\":300}"),
                                jsonCodec.readTree("{\"group\":\"energy\",\"syncFinanceField\":\"kwhRate\"}")
                        )
                )
        );
        when(makerQuoteSettingsService.getSettings()).thenReturn(response);

        mockMvc.perform(get("/maker/quote-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customized").value(false))
                .andExpect(jsonPath("$.data.companyRates.kwhRate").value(0.92))
                .andExpect(jsonPath("$.data.companyRates.minOrderValue").value(20.0))
                .andExpect(jsonPath("$.data.modules[0].code").value("POWER"))
                .andExpect(jsonPath("$.data.modules[0].config.defaultPowerWatts").value(300))
                .andExpect(jsonPath("$.data.modules[0].uiSchema.syncFinanceField").value("kwhRate"));
    }

    @Test
    void updateSettingsDelegatesValidatedModularPayloadToService() throws Exception {
        MakerQuoteSettingsBundleResponse response = new MakerQuoteSettingsBundleResponse(
                true,
                Instant.parse("2026-11-23T21:10:00Z"),
                new MakerQuoteSettingsBundleResponse.CompanyRatesSnapshot(
                        new BigDecimal("0.9800"),
                        new BigDecimal("2.80"),
                        new BigDecimal("0.00")
                ),
                List.of(
                        new MakerQuoteModuleConfigResponse(
                                "POWER",
                                "Energia Elétrica",
                                "Consumo elétrico",
                                QuoteModuleType.BASE_COST,
                                10,
                                20,
                                true,
                                true,
                                true,
                                null,
                                jsonCodec.readTree("{\"kwhRate\":0.98,\"defaultPowerWatts\":320}"),
                                jsonCodec.createObjectNode()
                        )
                )
        );
        when(makerQuoteSettingsService.updateSettings(any())).thenReturn(response);

        mockMvc.perform(put("/maker/quote-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modules": [
                                    {
                                      "code": "POWER",
                                      "enabled": true,
                                      "executionOrder": 20,
                                      "config": { "kwhRate": 0.98, "defaultPowerWatts": 320 }
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customized").value(true))
                .andExpect(jsonPath("$.data.companyRates.kwhRate").value(0.98))
                .andExpect(jsonPath("$.data.modules[0].code").value("POWER"))
                .andExpect(jsonPath("$.data.modules[0].config.defaultPowerWatts").value(320));

        ArgumentCaptor<UpdateMakerQuoteModulesRequest> captor =
                ArgumentCaptor.forClass(UpdateMakerQuoteModulesRequest.class);
        verify(makerQuoteSettingsService).updateSettings(captor.capture());
        assertEquals(1, captor.getValue().modules().size());
        assertEquals("POWER", captor.getValue().modules().getFirst().code());
        assertTrue(captor.getValue().modules().getFirst().enabled());
    }
}
