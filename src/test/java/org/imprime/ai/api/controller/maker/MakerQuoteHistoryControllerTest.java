package org.imprime.ai.api.controller.maker;

import org.imprime.ai.api.http.request.maker.CalculateMakerQuoteRequest;
import org.imprime.ai.api.http.request.maker.CreateMakerQuoteRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteStatusRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteCalculationResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteHistoryResponse;
import org.imprime.ai.api.http.response.maker.QuotePartBreakdownResponse;
import org.imprime.ai.api.model.enums.QuoteHistoryStatus;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.service.MakerQuoteCalculationService;
import org.imprime.ai.api.service.MakerQuoteHistoryService;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MakerQuoteHistoryControllerTest {

    @Mock
    private MakerQuoteHistoryService makerQuoteHistoryService;
    @Mock
    private MakerQuoteCalculationService makerQuoteCalculationService;

    private MockMvc mockMvc;
    private JsonCodec jsonCodec;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonCodec();
        MakerQuoteHistoryController controller =
                new MakerQuoteHistoryController(makerQuoteHistoryService, makerQuoteCalculationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void listQuotesDelegatesFiltersAndPageableToService() throws Exception {
        MakerQuoteHistoryResponse item = sampleResponse("quote-1", QuoteHistoryStatus.SENT);
        PageResponse<MakerQuoteHistoryResponse> page = PageResponse.from(
                new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1)
        );
        when(makerQuoteHistoryService.listQuotes(eq(QuoteHistoryStatus.SENT), eq("chaveiro"), any()))
                .thenReturn(page);

        mockMvc.perform(get("/maker/quotes")
                        .param("status", "SENT")
                        .param("q", "chaveiro")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].guid").value("quote-1"))
                .andExpect(jsonPath("$.data.content[0].quoteStatus").value("SENT"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void createQuoteReturns201AndPassesSnapshots() throws Exception {
        MakerQuoteHistoryResponse created = sampleResponse("quote-created", QuoteHistoryStatus.DRAFT);
        when(makerQuoteHistoryService.createQuote(any(CreateMakerQuoteRequest.class))).thenReturn(created);

        String body = """
                {
                  "title": "Chaveiro NFC",
                  "clientName": "Ana",
                  "quoteStatus": "DRAFT",
                  "quantity": 3,
                  "unitPrice": 18.50,
                  "finalPriceTotal": 55.50,
                  "productionCostTotal": 30.00,
                  "estimatedProfitTotal": 25.50,
                  "inputSnapshot": {"quantity": 3},
                  "settingsSnapshot": {"kwhRate": 0.92},
                  "resultSnapshot": {"isValid": true}
                }
                """;

        mockMvc.perform(post("/maker/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.guid").value("quote-created"));

        ArgumentCaptor<CreateMakerQuoteRequest> captor = ArgumentCaptor.forClass(CreateMakerQuoteRequest.class);
        verify(makerQuoteHistoryService).createQuote(captor.capture());
        assertEquals("Chaveiro NFC", captor.getValue().title());
        assertEquals(3, captor.getValue().quantity());
        assertEquals(3, captor.getValue().inputSnapshot().path("quantity").asInt());
    }

    @Test
    void updateQuoteStatusAndDeleteDelegateToService() throws Exception {
        MakerQuoteHistoryResponse updated = sampleResponse("quote-1", QuoteHistoryStatus.APPROVED);
        when(makerQuoteHistoryService.updateQuoteStatus(eq("quote-1"), any(UpdateMakerQuoteStatusRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(patch("/maker/quotes/quote-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteStatus\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quoteStatus").value("APPROVED"));

        mockMvc.perform(delete("/maker/quotes/quote-1"))
                .andExpect(status().isNoContent());

        verify(makerQuoteHistoryService).deleteQuote("quote-1");
    }

    @Test
    void calculateQuoteReturnsRealTimeBreakdownAndPassesModuleOverrides() throws Exception {
        MakerQuoteCalculationResponse.BreakdownAmountsResponse total = new MakerQuoteCalculationResponse.BreakdownAmountsResponse(
                new BigDecimal("14.40"),
                new BigDecimal("0.97"),
                new BigDecimal("8.75"),
                new BigDecimal("24.12"),
                new BigDecimal("2.41"),
                new BigDecimal("8.75"),
                new BigDecimal("5.00"),
                new BigDecimal("4.00"),
                new BigDecimal("44.28"),
                new BigDecimal("66.42"),
                new BigDecimal("6.64"),
                new BigDecimal("0.00"),
                new BigDecimal("59.78"),
                new BigDecimal("44.28"),
                new BigDecimal("15.50")
        );
        MakerQuoteCalculationResponse response = new MakerQuoteCalculationResponse(
                true,
                new MakerQuoteCalculationResponse.EffectiveParametersResponse(
                        2, "per_unit", null, 1, new BigDecimal("1.7500"), new BigDecimal("3.5000"),
                        new BigDecimal("60.00"), new BigDecimal("120.00"),
                        new BigDecimal("300.00"), "printer",
                        new BigDecimal("0.525"), new BigDecimal("1.050"),
                        new BigDecimal("2.50"), "settings_rate",
                        true, 15, new BigDecimal("10.00"), new BigDecimal("50.00"),
                        new BigDecimal("0.00"), new BigDecimal("10.00"), "PROFIT_MARKUP",
                        "per_order"
                ),
                List.of(new QuotePartBreakdownResponse(
                        "MATERIAL", "Material / Filamento", QuoteModuleType.BASE_COST, 10,
                        true, false, new BigDecimal("7.20"), new BigDecimal("14.40"),
                        new BigDecimal("32.52"), "60 g/un · 120 g total", Map.of("filamentCount", 1)
                )),
                new MakerQuoteCalculationResponse.BreakdownSummaryResponse(
                        total, total, new BigDecimal("25.93"), new BigDecimal("35.00")
                ),
                List.of(),
                List.of()
        );
        when(makerQuoteCalculationService.calculateQuote(any(CalculateMakerQuoteRequest.class))).thenReturn(response);

        String body = """
                {
                  "quantity": 2,
                  "inputScope": "per_unit",
                  "printTimeHours": 1,
                  "printTimeMinutes": 45,
                  "printer": {
                    "mode": "select",
                    "printerId": "printer-guid-01",
                    "manualPowerWatts": 300
                  },
                  "filaments": [
                    {
                      "entryId": "fil-1",
                      "mode": "select",
                      "filamentId": "filament-guid-01",
                      "weightGrams": 60
                    }
                  ],
                  "requiresLabor": true,
                  "laborTimeMinutes": 15,
                  "moduleOverrides": {
                    "PROMOTION_DISCOUNT": {
                      "enabled": true,
                      "config": { "defaultDiscountPercent": 10.00 }
                    }
                  }
                }
                """;

        mockMvc.perform(post("/maker/quotes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isValid").value(true))
                .andExpect(jsonPath("$.data.parts[0].code").value("MATERIAL"))
                .andExpect(jsonPath("$.data.breakdown.total.suggestedFinalPrice").value(59.78));

        ArgumentCaptor<CalculateMakerQuoteRequest> captor = ArgumentCaptor.forClass(CalculateMakerQuoteRequest.class);
        verify(makerQuoteCalculationService).calculateQuote(captor.capture());
        assertEquals(2, captor.getValue().quantity());
        assertEquals(Boolean.TRUE, captor.getValue().moduleOverrides().get("PROMOTION_DISCOUNT").enabled());
    }

    @Test
    void calculateQuoteDeserializesPerPlateAndExtraCostsScopeFromJson() throws Exception {
        MakerQuoteCalculationResponse response = new MakerQuoteCalculationResponse(
                true,
                new MakerQuoteCalculationResponse.EffectiveParametersResponse(
                        500, "per_plate", 24, 21,
                        new BigDecimal("3.3333"), new BigDecimal("69.4444"),
                        new BigDecimal("5.54"), new BigDecimal("2770.83"),
                        new BigDecimal("300.00"), "manual",
                        new BigDecimal("0.042"), new BigDecimal("20.833"),
                        new BigDecimal("2.00"), "manual",
                        false, 0, new BigDecimal("10.00"), new BigDecimal("50.00"),
                        new BigDecimal("0.00"), new BigDecimal("0.00"), "PROFIT_MARKUP",
                        "per_unit"
                ),
                List.of(),
                new MakerQuoteCalculationResponse.BreakdownSummaryResponse(
                        new MakerQuoteCalculationResponse.BreakdownAmountsResponse(
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("175.00"),
                                new BigDecimal("424.37"), new BigDecimal("636.56"), BigDecimal.ZERO,
                                BigDecimal.ZERO, new BigDecimal("636.56"), new BigDecimal("424.37"), new BigDecimal("212.19")
                        ),
                        new MakerQuoteCalculationResponse.BreakdownAmountsResponse(
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("0.35"),
                                new BigDecimal("0.85"), new BigDecimal("1.27"), BigDecimal.ZERO,
                                BigDecimal.ZERO, new BigDecimal("1.27"), new BigDecimal("0.85"), new BigDecimal("0.42")
                        ),
                        new BigDecimal("33.33"),
                        new BigDecimal("50.00")
                ),
                List.of(),
                List.of()
        );
        when(makerQuoteCalculationService.calculateQuote(any(CalculateMakerQuoteRequest.class))).thenReturn(response);

        String body = """
                {
                  "quantity": 500,
                  "inputScope": "per_plate",
                  "unitsPerPlate": 24,
                  "printTimeHours": 3,
                  "printTimeMinutes": 20,
                  "printer": {
                    "mode": "manual",
                    "manualPowerWatts": 300
                  },
                  "filaments": [
                    { "entryId": "fil-1", "mode": "manual", "costPerKg": 90, "weightGrams": 94 },
                    { "entryId": "fil-2", "mode": "manual", "costPerKg": 90, "weightGrams": 39 }
                  ],
                  "requiresLabor": false,
                  "extraCosts": 0.35,
                  "extraCostsDescription": "Argola",
                  "extraCostsScope": "per_unit",
                  "moduleOverrides": {}
                }
                """;

        mockMvc.perform(post("/maker/quotes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effective.inputScope").value("per_plate"))
                .andExpect(jsonPath("$.data.effective.unitsPerPlate").value(24))
                .andExpect(jsonPath("$.data.effective.estimatedPlates").value(21))
                .andExpect(jsonPath("$.data.effective.extraCostsScope").value("per_unit"));

        ArgumentCaptor<CalculateMakerQuoteRequest> captor = ArgumentCaptor.forClass(CalculateMakerQuoteRequest.class);
        verify(makerQuoteCalculationService).calculateQuote(captor.capture());
        assertEquals(500, captor.getValue().quantity());
        assertEquals("per_plate", captor.getValue().inputScope());
        assertEquals(24, captor.getValue().unitsPerPlate());
        assertEquals("per_unit", captor.getValue().extraCostsScope());
        assertEquals(2, captor.getValue().filaments().size());
        assertEquals(0, new BigDecimal("94").compareTo(captor.getValue().filaments().get(0).weightGrams()));
        assertEquals(0, new BigDecimal("39").compareTo(captor.getValue().filaments().get(1).weightGrams()));
    }

    private MakerQuoteHistoryResponse sampleResponse(String guid, QuoteHistoryStatus status) {
        JsonNode emptyObj = jsonCodec.readObject("{}", JsonNode.class);
        return new MakerQuoteHistoryResponse(
                guid,
                "Chaveiro NFC",
                "Ana",
                status,
                3,
                new BigDecimal("18.50"),
                new BigDecimal("55.50"),
                new BigDecimal("30.00"),
                new BigDecimal("25.50"),
                emptyObj,
                emptyObj,
                emptyObj,
                Instant.parse("2026-11-22T21:00:00Z"),
                null
        );
    }
}
