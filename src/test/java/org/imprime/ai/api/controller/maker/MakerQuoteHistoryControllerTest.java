package org.imprime.ai.api.controller.maker;

import org.imprime.ai.api.http.request.maker.CreateMakerQuoteRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteStatusRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteHistoryResponse;
import org.imprime.ai.api.model.enums.QuoteHistoryStatus;
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

    private MockMvc mockMvc;
    private JsonCodec jsonCodec;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonCodec();
        MakerQuoteHistoryController controller = new MakerQuoteHistoryController(makerQuoteHistoryService);
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
