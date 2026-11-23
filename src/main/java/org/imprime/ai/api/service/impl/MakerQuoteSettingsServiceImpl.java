package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteSettingsRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.QuoteDepreciationMode;
import org.imprime.ai.api.model.enums.QuotePricingStrategy;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerQuoteSettingsRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerQuoteSettingsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MakerQuoteSettingsServiceImpl implements MakerQuoteSettingsService {
    public static final int DEFAULT_POWER_WATTS = 300;
    public static final QuoteDepreciationMode DEFAULT_DEPRECIATION_MODE = QuoteDepreciationMode.FIXED_HOURLY;
    public static final BigDecimal DEFAULT_MACHINE_PRICE = new BigDecimal("3500.00");
    public static final int DEFAULT_MACHINE_LIFESPAN_HOURS = 3000;
    public static final BigDecimal DEFAULT_MAINTENANCE_HOURLY_COST = new BigDecimal("0.50");
    public static final BigDecimal DEFAULT_FAILURE_RATE_PERCENT = new BigDecimal("10.00");
    public static final BigDecimal DEFAULT_LABOR_HOURLY_RATE = new BigDecimal("35.00");
    public static final BigDecimal DEFAULT_FIXED_PACKAGING_COST = new BigDecimal("5.00");
    public static final BigDecimal DEFAULT_PROFIT_MARGIN_PERCENT = new BigDecimal("50.00");
    public static final BigDecimal DEFAULT_ADDITIONAL_FEE_PERCENT = new BigDecimal("0.00");
    public static final QuotePricingStrategy DEFAULT_PRICING_STRATEGY = QuotePricingStrategy.MARKUP;
    private static final BigDecimal MAX_COMBINED_PERCENT = new BigDecimal("95.00");

    private final MakerCompanyResolver makerCompanyResolver;
    private final MakerQuoteSettingsRepository quoteSettingsRepository;
    private final CompanyRepository companyRepository;

    @Override
    public MakerQuoteSettingsResponse getSettings() {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Optional<MakerQuoteSettings> existing = quoteSettingsRepository.findByCompanyIdAndStatus(
                company.getId(), StatusCd.ACTIVE
        );

        if (existing.isEmpty()) {
            return new MakerQuoteSettingsResponse(
                    resolveKwhRate(company),
                    DEFAULT_POWER_WATTS,
                    DEFAULT_DEPRECIATION_MODE,
                    resolveDepreciationRate(company),
                    DEFAULT_MACHINE_PRICE,
                    DEFAULT_MACHINE_LIFESPAN_HOURS,
                    DEFAULT_MAINTENANCE_HOURLY_COST,
                    DEFAULT_FAILURE_RATE_PERCENT,
                    DEFAULT_LABOR_HOURLY_RATE,
                    DEFAULT_FIXED_PACKAGING_COST,
                    DEFAULT_PROFIT_MARGIN_PERCENT,
                    DEFAULT_ADDITIONAL_FEE_PERCENT,
                    DEFAULT_PRICING_STRATEGY,
                    false,
                    null
            );
        }

        return toResponse(company, existing.get(), true);
    }

    @Override
    @Transactional
    public MakerQuoteSettingsResponse updateSettings(UpdateMakerQuoteSettingsRequest request) {
        if (request.additionalFeePercent().compareTo(MAX_COMBINED_PERCENT) >= 0) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        if (QuotePricingStrategy.MARGIN_ON_PRICE.equals(request.pricingStrategy())
                && request.defaultProfitMarginPercent().add(request.additionalFeePercent())
                .compareTo(MAX_COMBINED_PERCENT) >= 0) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        Company company = makerCompanyResolver.requireActiveMakerCompany();
        company.setKwhRate(request.kwhRate().setScale(4, RoundingMode.HALF_UP));
        company.setHourlyDepreciationRate(request.hourlyDepreciationRate().setScale(2, RoundingMode.HALF_UP));
        Company savedCompany = companyRepository.save(company);

        MakerQuoteSettings entity = quoteSettingsRepository
                .findByCompanyIdAndStatus(savedCompany.getId(), StatusCd.ACTIVE)
                .orElseGet(() -> {
                    MakerQuoteSettings created = new MakerQuoteSettings();
                    created.setCompanyId(savedCompany.getId());
                    created.setStatus(StatusCd.ACTIVE);
                    return created;
                });

        entity.setDefaultPowerWatts(request.defaultPowerWatts());
        entity.setDepreciationMode(request.depreciationMode());
        entity.setDefaultMachinePrice(scale2(request.defaultMachinePrice()));
        entity.setDefaultMachineLifespanHours(request.defaultMachineLifespanHours());
        entity.setDefaultMaintenanceHourlyCost(scale2(request.defaultMaintenanceHourlyCost()));
        entity.setFailureRatePercent(scale2(request.failureRatePercent()));
        entity.setLaborHourlyRate(scale2(request.laborHourlyRate()));
        entity.setFixedPackagingCost(scale2(request.fixedPackagingCost()));
        entity.setDefaultProfitMarginPercent(scale2(request.defaultProfitMarginPercent()));
        entity.setAdditionalFeePercent(scale2(request.additionalFeePercent()));
        entity.setPricingStrategy(request.pricingStrategy());

        MakerQuoteSettings savedEntity = quoteSettingsRepository.save(entity);
        return toResponse(savedCompany, savedEntity, true);
    }

    private MakerQuoteSettingsResponse toResponse(
            Company company,
            MakerQuoteSettings entity,
            boolean customized) {
        Instant updatedAt = entity.getUpdateDt() != null ? entity.getUpdateDt() : entity.getCreateDt();
        return new MakerQuoteSettingsResponse(
                resolveKwhRate(company),
                entity.getDefaultPowerWatts() != null ? entity.getDefaultPowerWatts() : DEFAULT_POWER_WATTS,
                entity.getDepreciationMode() != null ? entity.getDepreciationMode() : DEFAULT_DEPRECIATION_MODE,
                resolveDepreciationRate(company),
                entity.getDefaultMachinePrice() != null ? entity.getDefaultMachinePrice() : DEFAULT_MACHINE_PRICE,
                entity.getDefaultMachineLifespanHours() != null
                        ? entity.getDefaultMachineLifespanHours()
                        : DEFAULT_MACHINE_LIFESPAN_HOURS,
                entity.getDefaultMaintenanceHourlyCost() != null
                        ? entity.getDefaultMaintenanceHourlyCost()
                        : DEFAULT_MAINTENANCE_HOURLY_COST,
                entity.getFailureRatePercent() != null ? entity.getFailureRatePercent() : DEFAULT_FAILURE_RATE_PERCENT,
                entity.getLaborHourlyRate() != null ? entity.getLaborHourlyRate() : DEFAULT_LABOR_HOURLY_RATE,
                entity.getFixedPackagingCost() != null ? entity.getFixedPackagingCost() : DEFAULT_FIXED_PACKAGING_COST,
                entity.getDefaultProfitMarginPercent() != null
                        ? entity.getDefaultProfitMarginPercent()
                        : DEFAULT_PROFIT_MARGIN_PERCENT,
                entity.getAdditionalFeePercent() != null
                        ? entity.getAdditionalFeePercent()
                        : DEFAULT_ADDITIONAL_FEE_PERCENT,
                entity.getPricingStrategy() != null ? entity.getPricingStrategy() : DEFAULT_PRICING_STRATEGY,
                customized,
                updatedAt
        );
    }

    private BigDecimal resolveKwhRate(Company company) {
        return company.getKwhRate() != null ? company.getKwhRate() : MakerFinanceServiceImpl.DEFAULT_KWH_RATE;
    }

    private BigDecimal resolveDepreciationRate(Company company) {
        return company.getHourlyDepreciationRate() != null
                ? company.getHourlyDepreciationRate()
                : MakerFinanceServiceImpl.DEFAULT_HOURLY_DEPRECIATION_RATE;
    }

    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
