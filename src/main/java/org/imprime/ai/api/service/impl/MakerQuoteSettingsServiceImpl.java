package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteModulesRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteModuleConfigResponse;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsBundleResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.QuoteSettings;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.QuoteDepreciationMode;
import org.imprime.ai.api.model.enums.QuoteModuleType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.quote.ModularQuoteEngine;
import org.imprime.ai.api.quote.QuoteConfigReader;
import org.imprime.ai.api.quote.ResolvedQuoteModule;
import org.imprime.ai.api.quote.calculator.AdditionalFeeQuotePartCalculator;
import org.imprime.ai.api.repo.cache.MakerQuoteSettingsInMemoryCache;
import org.imprime.ai.api.repo.cache.QuoteSettingsInMemoryCache;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerQuoteSettingsRepository;
import org.imprime.ai.api.repo.db.QuoteSettingsRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerQuoteSettingsService;
import org.imprime.ai.api.util.JsonCodec;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MakerQuoteSettingsServiceImpl implements MakerQuoteSettingsService {
    private static final String MODULE_MATERIAL = "MATERIAL";
    private static final String MODULE_POWER = "POWER";
    private static final String MODULE_DEPRECIATION = "DEPRECIATION";
    private static final String MODULE_LABOR = "LABOR";
    private static final String MODULE_PACKAGING = "PACKAGING";
    private static final String MODULE_EXTRA_COSTS = "EXTRA_COSTS";
    private static final String MODULE_FAILURE_RESERVE = "FAILURE_RESERVE";
    private static final String MODULE_PROFIT_MARKUP = "PROFIT_MARKUP";
    private static final String MODULE_PROFIT_MARGIN = "PROFIT_MARGIN";
    private static final String MODULE_PROMOTION_DISCOUNT = "PROMOTION_DISCOUNT";
    private static final String MODULE_ADDITIONAL_FEE = "ADDITIONAL_FEE";

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal MAX_DIVISOR_EXCLUSIVE = new BigDecimal("95.00");

    private final MakerCompanyResolver makerCompanyResolver;
    private final CompanyRepository companyRepository;
    private final QuoteSettingsRepository quoteSettingsRepository;
    private final MakerQuoteSettingsRepository makerQuoteSettingsRepository;
    private final QuoteSettingsInMemoryCache quoteSettingsInMemoryCache;
    private final MakerQuoteSettingsInMemoryCache makerQuoteSettingsInMemoryCache;
    private final ModularQuoteEngine modularQuoteEngine;
    private final JsonCodec jsonCodec;

    @Override
    public MakerQuoteSettingsBundleResponse getSettings() {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<QuoteSettings> catalog = loadActiveCatalog();
        List<MakerQuoteSettings> makerRows = loadActiveMakerModulesWithCache(company.getId());
        return buildBundleResponse(company, catalog, makerRows);
    }

    @Override
    @Transactional
    public MakerQuoteSettingsBundleResponse updateSettings(UpdateMakerQuoteModulesRequest request) {
        if (request == null || request.modules() == null || request.modules().isEmpty()) {
            throw new BadRequestException("A lista de módulos é obrigatória.", MessageCd.VALIDATION_ERROR);
        }

        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<QuoteSettings> catalog = loadActiveCatalog();
        Map<String, QuoteSettings> catalogByCode = indexCatalogByCode(catalog);
        Map<String, UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem> submittedByCode =
                parseAndValidateSubmittedItems(request.modules(), catalogByCode);

        List<MakerQuoteSettings> existingRows = makerQuoteSettingsRepository
                .findByCompanyIdAndStatusOrderByExecutionOrderAscIdAsc(company.getId(), StatusCd.ACTIVE);
        Map<Long, MakerQuoteSettings> existingByCatalogId = new LinkedHashMap<>();
        for (MakerQuoteSettings row : existingRows) {
            if (row.getQuoteSettingsId() != null) {
                existingByCatalogId.put(row.getQuoteSettingsId(), row);
            }
        }

        List<PreparedModule> preparedModules = new ArrayList<>(catalog.size());
        Map<String, Integer> activePerMutualGroup = new LinkedHashMap<>();

        for (QuoteSettings catalogModule : catalog) {
            String code = normalizeCode(catalogModule.getCode());
            MakerQuoteSettings existingRow = existingByCatalogId.get(catalogModule.getId());
            UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem submitted = submittedByCode.get(code);

            boolean defaultEnabled = !Boolean.FALSE.equals(catalogModule.getDefaultEnabled());
            boolean enabled = submitted != null
                    ? Boolean.TRUE.equals(submitted.enabled())
                    : (existingRow != null && existingRow.getEnabled() != null ? existingRow.getEnabled() : defaultEnabled);

            if (Boolean.FALSE.equals(catalogModule.getCanDisable()) && !enabled) {
                throw new BadRequestException(
                        "O módulo \"" + catalogModule.getTitle() + "\" é obrigatório e não pode ser desativado.",
                        MessageCd.VALIDATION_ERROR
                );
            }

            Integer defaultOrder = catalogModule.getExecutionOrder() != null ? catalogModule.getExecutionOrder() : 0;
            Integer existingOrder = existingRow != null && existingRow.getExecutionOrder() != null
                    ? existingRow.getExecutionOrder()
                    : defaultOrder;
            int executionOrder = submitted != null && submitted.executionOrder() != null
                    ? submitted.executionOrder()
                    : existingOrder;

            ObjectNode mergedConfig = QuoteConfigReader.mergeConfigs(
                    jsonCodec,
                    catalogModule.getDefaultConfigJson(),
                    existingRow != null ? existingRow.getConfigJson() : null,
                    null
            );

            // Se ainda não há customização salva para energia/depreciação, preserva tarifa da empresa como base
            if (existingRow == null) {
                if (MODULE_POWER.equals(code) && company.getKwhRate() != null) {
                    mergedConfig.put("kwhRate", scale4(company.getKwhRate()));
                } else if (MODULE_DEPRECIATION.equals(code) && company.getHourlyDepreciationRate() != null) {
                    mergedConfig.put("hourlyDepreciationRate", scale2(company.getHourlyDepreciationRate()));
                }
            }

            if (submitted != null && submitted.config() != null && !submitted.config().isEmpty()) {
                mergedConfig = QuoteConfigReader.mergeConfigs(
                        jsonCodec,
                        mergedConfig.toString(),
                        null,
                        submitted.config()
                );
            }

            validateModuleConfig(code, mergedConfig);

            String group = normalizeGroup(catalogModule.getMutualExclusionGroup());
            if (group != null && enabled) {
                activePerMutualGroup.merge(group, 1, Integer::sum);
                if (activePerMutualGroup.get(group) > 1) {
                    throw new BadRequestException(
                            "Apenas um módulo pode permanecer ativo no grupo exclusivo [" + group + "].",
                            MessageCd.VALIDATION_ERROR
                    );
                }
            }

            MakerQuoteSettings entity = existingRow != null ? existingRow : new MakerQuoteSettings();
            entity.setCompanyId(company.getId());
            entity.setQuoteSettingsId(catalogModule.getId());
            entity.setStatus(StatusCd.ACTIVE);
            entity.setEnabled(enabled);
            entity.setExecutionOrder(executionOrder);
            entity.setConfigJson(jsonCodec.toJson(mergedConfig));

            preparedModules.add(new PreparedModule(code, catalogModule, entity, mergedConfig));
        }

        validatePricingDivisorInvariant(preparedModules);
        syncCompanyRatesFromPreparedModules(company, preparedModules);
        Company savedCompany = companyRepository.save(company);

        List<MakerQuoteSettings> toSave = preparedModules.stream()
                .map(PreparedModule::entity)
                .toList();
        List<MakerQuoteSettings> savedRows = makerQuoteSettingsRepository.saveAll(toSave);

        makerQuoteSettingsInMemoryCache.evictAfterCommit(savedCompany.getId());
        return buildBundleResponse(savedCompany, catalog, savedRows);
    }

    private List<QuoteSettings> loadActiveCatalog() {
        List<QuoteSettings> cached = quoteSettingsInMemoryCache.getAllOrdered();
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        return quoteSettingsRepository.findByStatusOrderByPhaseOrderAscExecutionOrderAscIdAsc(StatusCd.ACTIVE);
    }

    private List<MakerQuoteSettings> loadActiveMakerModulesWithCache(Long companyId) {
        return makerQuoteSettingsInMemoryCache.findByCompanyId(companyId)
                .orElseGet(() -> {
                    List<MakerQuoteSettings> fromDb = makerQuoteSettingsRepository
                            .findByCompanyIdAndStatusOrderByExecutionOrderAscIdAsc(companyId, StatusCd.ACTIVE);
                    List<MakerQuoteSettings> copy = fromDb != null ? List.copyOf(fromDb) : List.of();
                    makerQuoteSettingsInMemoryCache.putCompanySettings(companyId, copy);
                    return copy;
                });
    }

    private Map<String, QuoteSettings> indexCatalogByCode(List<QuoteSettings> catalog) {
        Map<String, QuoteSettings> byCode = new LinkedHashMap<>();
        for (QuoteSettings entry : catalog) {
            if (entry != null && entry.getCode() != null && !entry.getCode().isBlank()) {
                byCode.put(normalizeCode(entry.getCode()), entry);
            }
        }
        return byCode;
    }

    private Map<String, UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem> parseAndValidateSubmittedItems(
            List<UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem> items,
            Map<String, QuoteSettings> catalogByCode) {
        Map<String, UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem> submittedByCode = new LinkedHashMap<>();
        for (UpdateMakerQuoteModulesRequest.MakerQuoteModuleUpdateItem item : items) {
            if (item == null || item.code() == null || item.code().isBlank()) {
                throw new BadRequestException("Código de módulo inválido.", MessageCd.VALIDATION_ERROR);
            }
            String normalizedCode = normalizeCode(item.code());
            if (!catalogByCode.containsKey(normalizedCode)) {
                throw new BadRequestException(
                        "Módulo de orçamento desconhecido: " + normalizedCode,
                        MessageCd.VALIDATION_ERROR
                );
            }
            if (submittedByCode.put(normalizedCode, item) != null) {
                throw new BadRequestException(
                        "Código de módulo duplicado na requisição: " + normalizedCode,
                        MessageCd.VALIDATION_ERROR
                );
            }
        }
        return submittedByCode;
    }

    private void validateModuleConfig(String code, ObjectNode config) {
        switch (code) {
            case MODULE_MATERIAL -> requireMin(
                    requireDecimal(config, "defaultCostPerKg", "Custo padrão do material"),
                    BigDecimal.ZERO, true,
                    "O custo padrão do material não pode ser negativo."
            );
            case MODULE_POWER -> {
                requireMin(
                        requireDecimal(config, "kwhRate", "Tarifa de energia (kWh)"),
                        BigDecimal.ZERO, false,
                        "A tarifa de energia (kWh) deve ser positiva."
                );
                requireMin(
                        requireDecimal(config, "defaultPowerWatts", "Potência padrão (W)"),
                        BigDecimal.ONE, true,
                        "A potência padrão (Watts) deve ser maior ou igual a 1."
                );
            }
            case MODULE_DEPRECIATION -> {
                String modeRaw = QuoteConfigReader.getString(config, "depreciationMode", "FIXED_HOURLY");
                try {
                    QuoteDepreciationMode.valueOf(modeRaw.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw new BadRequestException("Modo de depreciação inválido: " + modeRaw, MessageCd.VALIDATION_ERROR);
                }
                requireMin(
                        requireDecimal(config, "hourlyDepreciationRate", "Taxa horária de depreciação"),
                        BigDecimal.ZERO, true,
                        "A depreciação horária não pode ser negativa."
                );
                requireMin(
                        requireDecimal(config, "defaultMachinePrice", "Valor padrão da máquina"),
                        BigDecimal.ZERO, true,
                        "O valor padrão da máquina não pode ser negativo."
                );
                requireMin(
                        requireDecimal(config, "defaultMachineLifespanHours", "Vida útil padrão (horas)"),
                        BigDecimal.ONE, true,
                        "A vida útil padrão em horas deve ser positiva."
                );
                requireMin(
                        requireDecimal(config, "defaultMaintenanceHourlyCost", "Manutenção preventiva (R$/h)"),
                        BigDecimal.ZERO, true,
                        "O custo horário de manutenção não pode ser negativo."
                );
            }
            case MODULE_LABOR -> {
                requireMin(
                        requireDecimal(config, "laborHourlyRate", "Valor da hora técnica"),
                        BigDecimal.ZERO, true,
                        "O valor da hora de mão de obra não pode ser negativo."
                );
                requireMin(
                        requireDecimal(config, "defaultLaborMinutes", "Tempo sugerido padrão"),
                        BigDecimal.ZERO, true,
                        "O tempo sugerido de mão de obra não pode ser negativo."
                );
            }
            case MODULE_PACKAGING -> requireMin(
                    requireDecimal(config, "fixedPackagingCost", "Custo fixo de embalagem"),
                    BigDecimal.ZERO, true,
                    "O custo fixo de embalagem não pode ser negativo."
            );
            case MODULE_EXTRA_COSTS -> requireMin(
                    requireDecimal(config, "defaultExtraCost", "Valor padrão de custos extras"),
                    BigDecimal.ZERO, true,
                    "O custo extra padrão não pode ser negativo."
            );
            case MODULE_FAILURE_RESERVE -> {
                BigDecimal rate = requireDecimal(config, "failureRatePercent", "Taxa de reserva de falha (%)");
                requireRange(rate, BigDecimal.ZERO, true, HUNDRED, true,
                        "A taxa de falha deve estar entre 0% e 100%.");
                String applyOn = QuoteConfigReader.getString(config, "applyOn", "DIRECT_PRINT_COST");
                if (!"DIRECT_PRINT_COST".equalsIgnoreCase(applyOn)
                        && !"BASE_PRODUCTION_COST".equalsIgnoreCase(applyOn)) {
                    throw new BadRequestException(
                            "Base de incidência inválida para reserva de falha: " + applyOn,
                            MessageCd.VALIDATION_ERROR
                    );
                }
            }
            case MODULE_PROFIT_MARKUP -> requireMin(
                    requireDecimal(config, "profitMarginPercent", "Markup padrão (%)"),
                    BigDecimal.ZERO, true,
                    "O markup padrão não pode ser negativo."
            );
            case MODULE_PROFIT_MARGIN -> {
                BigDecimal margin = requireDecimal(config, "profitMarginPercent", "Margem sobre venda (%)");
                requireRange(margin, BigDecimal.ZERO, true, MAX_DIVISOR_EXCLUSIVE, false,
                        "A margem sobre venda deve estar entre 0% e 94,99%.");
            }
            case MODULE_PROMOTION_DISCOUNT -> {
                BigDecimal defaultDiscount = requireDecimal(config, "defaultDiscountPercent", "Desconto padrão (%)");
                BigDecimal maxDiscount = requireDecimal(config, "maxDiscountPercent", "Limite de desconto (%)");
                requireRange(defaultDiscount, BigDecimal.ZERO, true, HUNDRED, true,
                        "O desconto padrão deve estar entre 0% e 100%.");
                requireRange(maxDiscount, BigDecimal.ZERO, true, HUNDRED, true,
                        "O limite máximo de desconto deve estar entre 0% e 100%.");
            }
            case MODULE_ADDITIONAL_FEE -> {
                BigDecimal fee = AdditionalFeeQuotePartCalculator.extractFeePercent(config);
                requireRange(fee, BigDecimal.ZERO, true, MAX_DIVISOR_EXCLUSIVE, false,
                        "As taxas adicionais devem ser inferiores a 95%.");
            }
            default -> {
                // Módulos extensíveis sem regra fixa adicional neste serviço
            }
        }
    }

    private void validatePricingDivisorInvariant(List<PreparedModule> preparedModules) {
        BigDecimal totalEnabledFeePercent = BigDecimal.ZERO;
        PreparedModule enabledMarginModule = null;

        for (PreparedModule item : preparedModules) {
            if (!Boolean.TRUE.equals(item.entity().getEnabled())) {
                continue;
            }
            if (item.catalog().getQuoteType() == QuoteModuleType.FEE_TAX) {
                totalEnabledFeePercent = totalEnabledFeePercent.add(
                        AdditionalFeeQuotePartCalculator.extractFeePercent(item.config())
                );
            }
            if (MODULE_PROFIT_MARGIN.equals(item.code())) {
                enabledMarginModule = item;
            }
        }

        if (totalEnabledFeePercent.compareTo(MAX_DIVISOR_EXCLUSIVE) >= 0) {
            throw new BadRequestException(
                    "A soma das taxas adicionais ativas deve ser inferior a 95%.",
                    MessageCd.VALIDATION_ERROR
            );
        }

        if (enabledMarginModule != null) {
            BigDecimal marginPercent = requireDecimal(
                    enabledMarginModule.config(),
                    "profitMarginPercent",
                    "Margem sobre venda (%)"
            );
            if (marginPercent.add(totalEnabledFeePercent).compareTo(MAX_DIVISOR_EXCLUSIVE) >= 0) {
                throw new BadRequestException(
                        "A soma entre margem sobre o preço final e taxas adicionais deve ser inferior a 95%.",
                        MessageCd.VALIDATION_ERROR
                );
            }
        }
    }

    private void syncCompanyRatesFromPreparedModules(Company company, List<PreparedModule> preparedModules) {
        for (PreparedModule item : preparedModules) {
            if (MODULE_POWER.equals(item.code())) {
                BigDecimal kwhRate = requireDecimal(item.config(), "kwhRate", "Tarifa de energia (kWh)");
                company.setKwhRate(scale4(kwhRate));
            } else if (MODULE_DEPRECIATION.equals(item.code())) {
                BigDecimal depRate = requireDecimal(
                        item.config(),
                        "hourlyDepreciationRate",
                        "Taxa horária de depreciação"
                );
                company.setHourlyDepreciationRate(scale2(depRate));
            }
        }
    }

    private MakerQuoteSettingsBundleResponse buildBundleResponse(
            Company company,
            List<QuoteSettings> catalog,
            List<MakerQuoteSettings> makerRows) {
        Map<String, JsonNode> uiSchemaByCode = new LinkedHashMap<>();
        for (QuoteSettings entry : catalog) {
            if (entry != null && entry.getCode() != null) {
                uiSchemaByCode.put(
                        normalizeCode(entry.getCode()),
                        jsonCodec.readTree(entry.getUiSchemaJson())
                );
            }
        }

        List<ResolvedQuoteModule> resolvedModules =
                modularQuoteEngine.resolveModules(company, catalog, makerRows, null);

        List<MakerQuoteModuleConfigResponse> moduleResponses = resolvedModules.stream()
                .map(mod -> new MakerQuoteModuleConfigResponse(
                        mod.code(),
                        mod.title(),
                        mod.description(),
                        mod.quoteType(),
                        mod.phaseOrder(),
                        mod.executionOrder(),
                        mod.enabled(),
                        mod.canDisable(),
                        mod.allowQuoteOverride(),
                        mod.mutualExclusionGroup(),
                        mod.effectiveConfig(),
                        uiSchemaByCode.getOrDefault(mod.code(), jsonCodec.createObjectNode())
                ))
                .toList();

        boolean customized = makerRows != null && !makerRows.isEmpty();
        Instant latestUpdatedAt = null;
        if (customized) {
            for (MakerQuoteSettings row : makerRows) {
                Instant ts = row.getUpdateDt() != null ? row.getUpdateDt() : row.getCreateDt();
                if (ts != null && (latestUpdatedAt == null || ts.isAfter(latestUpdatedAt))) {
                    latestUpdatedAt = ts;
                }
            }
        }

        MakerQuoteSettingsBundleResponse.CompanyRatesSnapshot companyRates =
                new MakerQuoteSettingsBundleResponse.CompanyRatesSnapshot(
                        scale4(company.getKwhRate() != null
                                ? company.getKwhRate()
                                : MakerFinanceServiceImpl.DEFAULT_KWH_RATE),
                        scale2(company.getHourlyDepreciationRate() != null
                                ? company.getHourlyDepreciationRate()
                                : MakerFinanceServiceImpl.DEFAULT_HOURLY_DEPRECIATION_RATE),
                        scale2(company.getMinOrderValue() != null ? company.getMinOrderValue() : BigDecimal.ZERO)
                );

        return new MakerQuoteSettingsBundleResponse(
                customized,
                latestUpdatedAt,
                companyRates,
                moduleResponses
        );
    }

    private BigDecimal requireDecimal(JsonNode config, String key, String fieldLabel) {
        if (!QuoteConfigReader.hasNonNullKey(config, key)) {
            throw new BadRequestException("Campo obrigatório ausente: " + fieldLabel, MessageCd.VALIDATION_ERROR);
        }
        JsonNode value = config.get(key);
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isString()) {
            try {
                return new BigDecimal(value.asString().trim());
            } catch (NumberFormatException ignored) {
                throw new BadRequestException("Valor numérico inválido para " + fieldLabel, MessageCd.VALIDATION_ERROR);
            }
        }
        throw new BadRequestException("Valor numérico inválido para " + fieldLabel, MessageCd.VALIDATION_ERROR);
    }

    private void requireMin(BigDecimal value, BigDecimal min, boolean inclusive, String message) {
        int cmp = value.compareTo(min);
        if (inclusive ? cmp < 0 : cmp <= 0) {
            throw new BadRequestException(message, MessageCd.VALIDATION_ERROR);
        }
    }

    private void requireRange(
            BigDecimal value,
            BigDecimal min,
            boolean minInclusive,
            BigDecimal max,
            boolean maxInclusive,
            String message) {
        int minCmp = value.compareTo(min);
        int maxCmp = value.compareTo(max);
        boolean minOk = minInclusive ? minCmp >= 0 : minCmp > 0;
        boolean maxOk = maxInclusive ? maxCmp <= 0 : maxCmp < 0;
        if (!minOk || !maxOk) {
            throw new BadRequestException(message, MessageCd.VALIDATION_ERROR);
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeGroup(String group) {
        return group != null && !group.isBlank() ? group.trim().toUpperCase(Locale.ROOT) : null;
    }

    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale4(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private record PreparedModule(
            String code,
            QuoteSettings catalog,
            MakerQuoteSettings entity,
            ObjectNode config
    ) {
    }
}
