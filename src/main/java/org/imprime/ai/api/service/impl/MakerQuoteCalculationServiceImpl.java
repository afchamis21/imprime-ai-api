package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CalculateMakerQuoteRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteCalculationResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.quote.ModularQuoteEngine;
import org.imprime.ai.api.quote.QuoteCalculationResult;
import org.imprime.ai.api.quote.QuoteInput;
import org.imprime.ai.api.repo.cache.MakerQuoteSettingsInMemoryCache;
import org.imprime.ai.api.repo.cache.QuoteSettingsInMemoryCache;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerQuoteSettingsRepository;
import org.imprime.ai.api.repo.db.QuoteSettingsRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerQuoteCalculationService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MakerQuoteCalculationServiceImpl implements MakerQuoteCalculationService {
    private final MakerCompanyResolver makerCompanyResolver;
    private final QuoteSettingsInMemoryCache quoteSettingsInMemoryCache;
    private final QuoteSettingsRepository quoteSettingsRepository;
    private final MakerQuoteSettingsInMemoryCache makerQuoteSettingsInMemoryCache;
    private final MakerQuoteSettingsRepository makerQuoteSettingsRepository;
    private final AssetRepository assetRepository;
    private final FilamentRepository filamentRepository;
    private final ModularQuoteEngine modularQuoteEngine;

    @Override
    public MakerQuoteCalculationResponse calculateQuote(CalculateMakerQuoteRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<QuoteSettings> catalog = loadActiveCatalog();
        List<MakerQuoteSettings> makerModules = loadActiveMakerModulesWithCache(company.getId());

        QuoteInput quoteInput = request != null ? request.toQuoteInput() : null;
        List<Asset> printers = needsPrinterLookup(quoteInput)
                ? safeList(assetRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE))
                : List.of();
        List<Filament> filaments = needsFilamentLookup(quoteInput)
                ? safeList(filamentRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE))
                : List.of();

        QuoteCalculationResult result = modularQuoteEngine.calculate(
                quoteInput,
                company,
                catalog,
                makerModules,
                printers,
                filaments
        );
        return MakerQuoteCalculationResponse.from(result);
    }

    private List<QuoteSettings> loadActiveCatalog() {
        List<QuoteSettings> cached = quoteSettingsInMemoryCache.getAllOrdered();
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        return safeList(quoteSettingsRepository.findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE));
    }

    private List<MakerQuoteSettings> loadActiveMakerModulesWithCache(Long companyId) {
        return makerQuoteSettingsInMemoryCache.findByCompanyId(companyId)
                .orElseGet(() -> {
                    List<MakerQuoteSettings> copy = safeList(
                            makerQuoteSettingsRepository.findByCompanyIdAndStatusOrderByExecutionOrderAscIdAsc(
                                    companyId,
                                    StatusCd.ACTIVE
                            )
                    );
                    makerQuoteSettingsInMemoryCache.putCompanySettings(companyId, copy);
                    return copy;
                });
    }

    private boolean needsPrinterLookup(QuoteInput input) {
        if (input == null || input.printer() == null) {
            return false;
        }
        QuoteInput.PrinterInput printer = input.printer();
        return "select".equalsIgnoreCase(printer.mode())
                && printer.printerId() != null
                && !printer.printerId().isBlank();
    }

    private boolean needsFilamentLookup(QuoteInput input) {
        if (input == null || input.filaments() == null || input.filaments().isEmpty()) {
            return false;
        }
        for (QuoteInput.FilamentInput filament : input.filaments()) {
            if (filament != null
                    && "select".equalsIgnoreCase(filament.mode())
                    && filament.filamentId() != null
                    && !filament.filamentId().isBlank()) {
                return true;
            }
        }
        return false;
    }

    private <T> List<T> safeList(List<T> items) {
        return items != null ? List.copyOf(items) : List.of();
    }
}
