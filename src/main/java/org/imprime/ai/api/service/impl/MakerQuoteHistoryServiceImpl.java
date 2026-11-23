package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerQuoteHistoryService;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MakerQuoteHistoryServiceImpl implements MakerQuoteHistoryService {
    private final MakerCompanyResolver makerCompanyResolver;
    private final MakerQuoteHistoryRepository quoteHistoryRepository;
    private final JsonCodec jsonCodec;

    @Override
    public PageResponse<MakerQuoteHistoryResponse> listQuotes(
            QuoteHistoryStatus status,
            String query,
            Pageable pageable) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<MakerQuoteHistory> allQuotes = quoteHistoryRepository
                .findAllByCompanyIdAndStatusOrderByCreateDtDesc(company.getId(), StatusCd.ACTIVE);

        if (allQuotes.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        String normalizedQuery = query != null && !query.isBlank()
                ? query.trim().toLowerCase(Locale.ROOT)
                : null;

        List<MakerQuoteHistoryResponse> filtered = allQuotes.stream()
                .filter(q -> status == null || status.equals(q.getQuoteStatus()))
                .filter(q -> normalizedQuery == null
                        || containsIgnoreCase(q.getTitle(), normalizedQuery)
                        || containsIgnoreCase(q.getClientName(), normalizedQuery))
                .map(this::toResponse)
                .toList();

        return PageUtils.paginate(filtered, pageable);
    }

    @Override
    @Transactional
    public MakerQuoteHistoryResponse createQuote(CreateMakerQuoteRequest request) {
        validateObjectSnapshot(request.inputSnapshot());
        validateObjectSnapshot(request.settingsSnapshot());
        validateObjectSnapshot(request.resultSnapshot());

        Company company = makerCompanyResolver.requireActiveMakerCompany();

        MakerQuoteHistory entity = new MakerQuoteHistory();
        entity.setCompanyId(company.getId());
        entity.setStatus(StatusCd.ACTIVE);
        entity.setTitle(request.title().trim());
        entity.setClientName(normalizeNullableText(request.clientName()));
        entity.setQuoteStatus(request.quoteStatus() != null ? request.quoteStatus() : QuoteHistoryStatus.DRAFT);
        entity.setQuantity(request.quantity());
        entity.setUnitPrice(scale2(request.unitPrice()));
        entity.setFinalPriceTotal(scale2(request.finalPriceTotal()));
        entity.setProductionCostTotal(scale2(request.productionCostTotal()));
        entity.setEstimatedProfitTotal(scale2(request.estimatedProfitTotal()));
        entity.setInputSnapshotJson(jsonCodec.toJson(request.inputSnapshot()));
        entity.setSettingsSnapshotJson(jsonCodec.toJson(request.settingsSnapshot()));
        entity.setResultSnapshotJson(jsonCodec.toJson(request.resultSnapshot()));

        MakerQuoteHistory saved = quoteHistoryRepository.save(entity);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MakerQuoteHistoryResponse updateQuoteStatus(
            String quoteGuid,
            UpdateMakerQuoteStatusRequest request) {
        MakerQuoteHistory entity = requireOwnedActiveQuote(quoteGuid);
        entity.setQuoteStatus(request.quoteStatus());
        MakerQuoteHistory saved = quoteHistoryRepository.save(entity);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteQuote(String quoteGuid) {
        MakerQuoteHistory entity = requireOwnedActiveQuote(quoteGuid);
        entity.setStatus(StatusCd.INACTIVE);
        quoteHistoryRepository.save(entity);
    }

    private MakerQuoteHistory requireOwnedActiveQuote(String quoteGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerQuoteHistory entity = quoteHistoryRepository.findByGuidAndStatus(quoteGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.GENERIC_404));

        if (!Objects.equals(entity.getCompanyId(), company.getId())) {
            throw new ForbiddenException(MessageCd.FORBIDDEN);
        }
        return entity;
    }

    private MakerQuoteHistoryResponse toResponse(MakerQuoteHistory entity) {
        return new MakerQuoteHistoryResponse(
                entity.getGuid(),
                entity.getTitle(),
                entity.getClientName(),
                entity.getQuoteStatus(),
                entity.getQuantity(),
                entity.getUnitPrice(),
                entity.getFinalPriceTotal(),
                entity.getProductionCostTotal(),
                entity.getEstimatedProfitTotal(),
                readSnapshot(entity.getInputSnapshotJson()),
                readSnapshot(entity.getSettingsSnapshotJson()),
                readSnapshot(entity.getResultSnapshotJson()),
                entity.getCreateDt(),
                entity.getUpdateDt()
        );
    }

    private JsonNode readSnapshot(String rawJson) {
        JsonNode parsed = jsonCodec.readObject(rawJson, JsonNode.class);
        return parsed != null ? parsed : jsonCodec.readObject("{}", JsonNode.class);
    }

    private void validateObjectSnapshot(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
    }

    private String normalizeNullableText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean containsIgnoreCase(String source, String normalizedQuery) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
