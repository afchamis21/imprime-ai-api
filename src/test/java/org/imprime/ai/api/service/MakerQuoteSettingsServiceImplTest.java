package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.UpdateMakerQuoteSettingsRequest;
import org.imprime.ai.api.http.response.maker.MakerQuoteSettingsResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerQuoteSettings;
import org.imprime.ai.api.model.enums.QuoteDepreciationMode;
import org.imprime.ai.api.model.enums.QuotePricingStrategy;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.MakerQuoteSettingsRepository;
import org.imprime.ai.api.service.impl.MakerFinanceServiceImpl;
import org.imprime.ai.api.service.impl.MakerQuoteSettingsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerQuoteSettingsServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private MakerQuoteSettingsRepository quoteSettingsRepository;
    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    private MakerQuoteSettingsServiceImpl service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");
        company.setKwhRate(new BigDecimal("0.9800"));
        company.setHourlyDepreciationRate(new BigDecimal("1.80"));
    }

    @Test
    void getSettingsReturnsDefaultsCombinedWithCompanyRatesWhenNotCustomized() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        company.setKwhRate(null);
        company.setHourlyDepreciationRate(null);
        when(quoteSettingsRepository.findByCompanyIdAndStatus(10L, StatusCd.ACTIVE))
                .thenReturn(Optional.empty());

        MakerQuoteSettingsResponse response = service.getSettings();

        assertFalse(response.customized());
        assertNull(response.updatedAt());
        assertEquals(MakerFinanceServiceImpl.DEFAULT_KWH_RATE, response.kwhRate());
        assertEquals(MakerFinanceServiceImpl.DEFAULT_HOURLY_DEPRECIATION_RATE, response.hourlyDepreciationRate());
        assertEquals(MakerQuoteSettingsServiceImpl.DEFAULT_POWER_WATTS, response.defaultPowerWatts());
        assertEquals(QuoteDepreciationMode.FIXED_HOURLY, response.depreciationMode());
        assertEquals(QuotePricingStrategy.MARKUP, response.pricingStrategy());
        assertEquals(new BigDecimal("50.00"), response.defaultProfitMarginPercent());
    }

    @Test
    void getSettingsReturnsPersistedSettingsWithCompanyRates() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        MakerQuoteSettings persisted = new MakerQuoteSettings();
        persisted.setCompanyId(10L);
        persisted.setDefaultPowerWatts(350);
        persisted.setDepreciationMode(QuoteDepreciationMode.MACHINE_LIFECYCLE);
        persisted.setDefaultMachinePrice(new BigDecimal("4200.00"));
        persisted.setDefaultMachineLifespanHours(2500);
        persisted.setDefaultMaintenanceHourlyCost(new BigDecimal("0.75"));
        persisted.setFailureRatePercent(new BigDecimal("12.00"));
        persisted.setLaborHourlyRate(new BigDecimal("45.00"));
        persisted.setFixedPackagingCost(new BigDecimal("6.50"));
        persisted.setDefaultProfitMarginPercent(new BigDecimal("60.00"));
        persisted.setAdditionalFeePercent(new BigDecimal("4.99"));
        persisted.setPricingStrategy(QuotePricingStrategy.MARGIN_ON_PRICE);
        Instant updatedAt = Instant.parse("2026-11-22T20:00:00Z");
        persisted.setUpdateDt(updatedAt);

        when(quoteSettingsRepository.findByCompanyIdAndStatus(10L, StatusCd.ACTIVE))
                .thenReturn(Optional.of(persisted));

        MakerQuoteSettingsResponse response = service.getSettings();

        assertTrue(response.customized());
        assertEquals(updatedAt, response.updatedAt());
        assertEquals(new BigDecimal("0.9800"), response.kwhRate());
        assertEquals(new BigDecimal("1.80"), response.hourlyDepreciationRate());
        assertEquals(350, response.defaultPowerWatts());
        assertEquals(QuoteDepreciationMode.MACHINE_LIFECYCLE, response.depreciationMode());
        assertEquals(QuotePricingStrategy.MARGIN_ON_PRICE, response.pricingStrategy());
        assertEquals(new BigDecimal("45.00"), response.laborHourlyRate());
    }

    @Test
    void updateSettingsCreatesNewRecordAndSyncsCompanyRates() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(quoteSettingsRepository.findByCompanyIdAndStatus(10L, StatusCd.ACTIVE))
                .thenReturn(Optional.empty());
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteSettingsRepository.save(any(MakerQuoteSettings.class))).thenAnswer(inv -> {
            MakerQuoteSettings saved = inv.getArgument(0);
            saved.setId(50L);
            saved.setUpdateDt(Instant.parse("2026-11-22T20:15:00Z"));
            return saved;
        });

        UpdateMakerQuoteSettingsRequest request = new UpdateMakerQuoteSettingsRequest(
                new BigDecimal("1.05"),
                280,
                QuoteDepreciationMode.MACHINE_LIFECYCLE,
                new BigDecimal("2.20"),
                new BigDecimal("4500.00"),
                3200,
                new BigDecimal("0.60"),
                new BigDecimal("8.00"),
                new BigDecimal("42.00"),
                new BigDecimal("4.50"),
                new BigDecimal("55.00"),
                new BigDecimal("3.50"),
                QuotePricingStrategy.MARKUP
        );

        MakerQuoteSettingsResponse response = service.updateSettings(request);

        assertTrue(response.customized());
        assertEquals(new BigDecimal("1.0500"), company.getKwhRate());
        assertEquals(new BigDecimal("2.20"), company.getHourlyDepreciationRate());
        assertEquals(new BigDecimal("1.0500"), response.kwhRate());
        assertEquals(new BigDecimal("2.20"), response.hourlyDepreciationRate());
        assertEquals(280, response.defaultPowerWatts());
        assertEquals(QuoteDepreciationMode.MACHINE_LIFECYCLE, response.depreciationMode());
        assertEquals(new BigDecimal("42.00"), response.laborHourlyRate());

        ArgumentCaptor<MakerQuoteSettings> captor = ArgumentCaptor.forClass(MakerQuoteSettings.class);
        verify(quoteSettingsRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getCompanyId());
        assertEquals(StatusCd.ACTIVE, captor.getValue().getStatus());
        verify(companyRepository).save(company);
    }

    @Test
    void updateSettingsUpdatesExistingRecord() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        MakerQuoteSettings existing = new MakerQuoteSettings();
        existing.setId(77L);
        existing.setCompanyId(10L);
        existing.setStatus(StatusCd.ACTIVE);
        existing.setDefaultPowerWatts(300);

        when(quoteSettingsRepository.findByCompanyIdAndStatus(10L, StatusCd.ACTIVE))
                .thenReturn(Optional.of(existing));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));
        when(quoteSettingsRepository.save(any(MakerQuoteSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMakerQuoteSettingsRequest request = new UpdateMakerQuoteSettingsRequest(
                new BigDecimal("0.89"),
                320,
                QuoteDepreciationMode.FIXED_HOURLY,
                new BigDecimal("1.95"),
                new BigDecimal("3800.00"),
                2800,
                new BigDecimal("0.40"),
                new BigDecimal("10.00"),
                new BigDecimal("38.00"),
                new BigDecimal("5.50"),
                new BigDecimal("45.00"),
                new BigDecimal("2.00"),
                QuotePricingStrategy.MARGIN_ON_PRICE
        );

        MakerQuoteSettingsResponse response = service.updateSettings(request);

        verify(quoteSettingsRepository).save(existing);
        assertEquals(77L, existing.getId());
        assertEquals(320, existing.getDefaultPowerWatts());
        assertEquals(QuotePricingStrategy.MARGIN_ON_PRICE, response.pricingStrategy());
        assertEquals(new BigDecimal("0.8900"), response.kwhRate());
        assertEquals(new BigDecimal("1.95"), response.hourlyDepreciationRate());
    }

    @Test
    void updateSettingsRejectsMarginOnPriceWhenMarginPlusFeeReaches95Percent() {
        UpdateMakerQuoteSettingsRequest invalidRequest = new UpdateMakerQuoteSettingsRequest(
                new BigDecimal("0.92"),
                300,
                QuoteDepreciationMode.FIXED_HOURLY,
                new BigDecimal("2.50"),
                new BigDecimal("3500.00"),
                3000,
                new BigDecimal("0.50"),
                new BigDecimal("10.00"),
                new BigDecimal("35.00"),
                new BigDecimal("5.00"),
                new BigDecimal("85.00"),
                new BigDecimal("10.00"),
                QuotePricingStrategy.MARGIN_ON_PRICE
        );

        assertThrows(BadRequestException.class, () -> service.updateSettings(invalidRequest));
        verify(companyRepository, never()).save(any());
        verify(quoteSettingsRepository, never()).save(any());
    }
}
