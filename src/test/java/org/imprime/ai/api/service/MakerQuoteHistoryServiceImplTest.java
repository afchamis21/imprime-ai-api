package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.CreateMakerQuoteRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteStatusRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteHistoryResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerQuoteHistory;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.QuoteHistoryStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.MakerQuoteHistoryRepository;
import org.imprime.ai.api.service.impl.MakerQuoteHistoryServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerQuoteHistoryServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;

    @Mock
    private MakerQuoteHistoryRepository quoteHistoryRepository;

    private JsonCodec jsonCodec;
    private MakerQuoteHistoryServiceImpl service;
    private Company company;

    @BeforeEach
    void setUp() {
        jsonCodec = new JsonCodec();
        service = new MakerQuoteHistoryServiceImpl(makerCompanyResolver, quoteHistoryRepository, jsonCodec);
        company = new Company();
        company.setId(10L);
    }

    @Test
    void listQuotesFiltersByStatusAndQueryAndPaginates() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);

        MakerQuoteHistory matching = buildEntity("q-1", 10L, "Chaveiro NFC", "Maria Silva", QuoteHistoryStatus.SENT);
        MakerQuoteHistory wrongStatus = buildEntity("q-2", 10L, "Chaveiro Simples", "Maria", QuoteHistoryStatus.DRAFT);
        MakerQuoteHistory wrongQuery = buildEntity("q-3", 10L, "Suporte Headset", "Carlos", QuoteHistoryStatus.SENT);

        when(quoteHistoryRepository.findAllByCompanyIdAndStatusOrderByCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(matching, wrongStatus, wrongQuery));

        PageResponse<MakerQuoteHistoryResponse> page = service.listQuotes(
                QuoteHistoryStatus.SENT,
                "chaveiro",
                PageRequest.of(0, 10)
        );

        assertEquals(1, page.totalElements());
        assertEquals(1, page.content().size());
        assertEquals("q-1", page.content().getFirst().guid());
        assertEquals("Chaveiro NFC", page.content().getFirst().title());
        assertEquals(QuoteHistoryStatus.SENT, page.content().getFirst().quoteStatus());
    }

    @Test
    void createQuotePersistsJsonbSnapshotsAppliesDefaultDraftAndRoundsAmounts() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(quoteHistoryRepository.save(any(MakerQuoteHistory.class))).thenAnswer(inv -> {
            MakerQuoteHistory entity = inv.getArgument(0);
            entity.setGuid("quote-guid-100");
            entity.setCreateDt(Instant.parse("2026-11-22T21:00:00Z"));
            return entity;
        });

        JsonNode inputNode = jsonCodec.readObject("{\"quantity\":2,\"requiresLabor\":true}", JsonNode.class);
        JsonNode settingsNode = jsonCodec.readObject("{\"kwhRate\":0.92}", JsonNode.class);
        JsonNode resultNode = jsonCodec.readObject("{\"isValid\":true}", JsonNode.class);

        CreateMakerQuoteRequest request = new CreateMakerQuoteRequest(
                "  Vaso Geométrico  ",
                "   ",
                null,
                2,
                new BigDecimal("35.516"),
                new BigDecimal("71.034"),
                new BigDecimal("44.985"),
                new BigDecimal("22.495"),
                inputNode,
                settingsNode,
                resultNode
        );

        MakerQuoteHistoryResponse response = service.createQuote(request);

        ArgumentCaptor<MakerQuoteHistory> captor = ArgumentCaptor.forClass(MakerQuoteHistory.class);
        verify(quoteHistoryRepository).save(captor.capture());

        MakerQuoteHistory saved = captor.getValue();
        assertEquals(10L, saved.getCompanyId());
        assertEquals(StatusCd.ACTIVE, saved.getStatus());
        assertEquals("Vaso Geométrico", saved.getTitle());
        assertNull(saved.getClientName());
        assertEquals(QuoteHistoryStatus.DRAFT, saved.getQuoteStatus());
        assertEquals(new BigDecimal("35.52"), saved.getUnitPrice());
        assertEquals(new BigDecimal("71.03"), saved.getFinalPriceTotal());
        assertEquals(new BigDecimal("44.99"), saved.getProductionCostTotal());
        assertEquals(new BigDecimal("22.50"), saved.getEstimatedProfitTotal());

        assertEquals("quote-guid-100", response.guid());
        assertEquals(QuoteHistoryStatus.DRAFT, response.quoteStatus());
        assertTrue(response.inputSnapshot().path("requiresLabor").asBoolean());
        assertEquals(0.92, response.settingsSnapshot().path("kwhRate").asDouble(), 0.001);
        assertTrue(response.resultSnapshot().path("isValid").asBoolean());
    }

    @Test
    void createQuoteRejectsNonObjectSnapshot() {
        JsonNode arrayNode = jsonCodec.readObject("[\"invalid\"]", JsonNode.class);
        JsonNode objectNode = jsonCodec.readObject("{}", JsonNode.class);

        CreateMakerQuoteRequest request = new CreateMakerQuoteRequest(
                "Peça Teste",
                null,
                QuoteHistoryStatus.DRAFT,
                1,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00"),
                new BigDecimal("5.00"),
                arrayNode,
                objectNode,
                objectNode
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createQuote(request));
        assertEquals(MessageCd.VALIDATION_ERROR, ex.getMessageCd());
        verify(quoteHistoryRepository, never()).save(any());
    }

    @Test
    void updateQuoteStatusUpdatesOwnedQuoteAndChecksOwnership() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        MakerQuoteHistory entity = buildEntity("q-10", 10L, "Suporte", "João", QuoteHistoryStatus.SENT);
        when(quoteHistoryRepository.findByGuidAndStatus("q-10", StatusCd.ACTIVE)).thenReturn(Optional.of(entity));
        when(quoteHistoryRepository.save(any(MakerQuoteHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        MakerQuoteHistoryResponse response = service.updateQuoteStatus(
                "q-10",
                new UpdateMakerQuoteStatusRequest(QuoteHistoryStatus.APPROVED)
        );

        assertEquals(QuoteHistoryStatus.APPROVED, response.quoteStatus());
        verify(quoteHistoryRepository).save(entity);
    }

    @Test
    void updateQuoteStatusThrowsNotFoundAndForbidden() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(quoteHistoryRepository.findByGuidAndStatus("missing", StatusCd.ACTIVE)).thenReturn(Optional.empty());

        NotFoundException notFound = assertThrows(
                NotFoundException.class,
                () -> service.updateQuoteStatus("missing", new UpdateMakerQuoteStatusRequest(QuoteHistoryStatus.SENT))
        );
        assertEquals(MessageCd.GENERIC_404, notFound.getMessageCd());

        MakerQuoteHistory otherCompanyQuote = buildEntity("q-other", 99L, "Outro", null, QuoteHistoryStatus.DRAFT);
        when(quoteHistoryRepository.findByGuidAndStatus("q-other", StatusCd.ACTIVE))
                .thenReturn(Optional.of(otherCompanyQuote));

        ForbiddenException forbidden = assertThrows(
                ForbiddenException.class,
                () -> service.updateQuoteStatus("q-other", new UpdateMakerQuoteStatusRequest(QuoteHistoryStatus.SENT))
        );
        assertEquals(MessageCd.FORBIDDEN, forbidden.getMessageCd());
    }

    @Test
    void deleteQuoteMarksEntityInactive() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        MakerQuoteHistory entity = buildEntity("q-del", 10L, "Peça Remover", null, QuoteHistoryStatus.DRAFT);
        when(quoteHistoryRepository.findByGuidAndStatus("q-del", StatusCd.ACTIVE)).thenReturn(Optional.of(entity));

        service.deleteQuote("q-del");

        assertEquals(StatusCd.INACTIVE, entity.getStatus());
        verify(quoteHistoryRepository).save(entity);
    }

    private MakerQuoteHistory buildEntity(
            String guid,
            Long companyId,
            String title,
            String clientName,
            QuoteHistoryStatus quoteStatus) {
        MakerQuoteHistory entity = new MakerQuoteHistory();
        entity.setId(1L);
        entity.setGuid(guid);
        entity.setCompanyId(companyId);
        entity.setStatus(StatusCd.ACTIVE);
        entity.setTitle(title);
        entity.setClientName(clientName);
        entity.setQuoteStatus(quoteStatus);
        entity.setQuantity(2);
        entity.setUnitPrice(new BigDecimal("25.00"));
        entity.setFinalPriceTotal(new BigDecimal("50.00"));
        entity.setProductionCostTotal(new BigDecimal("30.00"));
        entity.setEstimatedProfitTotal(new BigDecimal("20.00"));
        entity.setInputSnapshotJson("{\"quantity\":2}");
        entity.setSettingsSnapshotJson("{\"kwhRate\":0.92}");
        entity.setResultSnapshotJson("{\"isValid\":true}");
        entity.setCreateDt(Instant.parse("2026-11-22T20:00:00Z"));
        return entity;
    }
}
