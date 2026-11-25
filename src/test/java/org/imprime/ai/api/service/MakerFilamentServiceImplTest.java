package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.AdjustFilamentStockRequest;
import org.imprime.ai.api.http.request.maker.CreateFilamentRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.FilamentResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.FilamentStockMovement;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.StockAdjustmentReason;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.repo.db.FilamentCatalogRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.FilamentStockMovementRepository;
import org.imprime.ai.api.service.impl.MakerFilamentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerFilamentServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private FilamentRepository filamentRepository;
    @Mock
    private FilamentStockMovementRepository movementRepository;
    @Mock
    private FilamentCatalogRepository filamentCatalogRepository;

    @InjectMocks
    private MakerFilamentServiceImpl service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");
    }

    @Test
    void createFilamentRecordsInitialPurchaseMovement() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(filamentRepository.save(any(Filament.class))).thenAnswer(inv -> {
            Filament f = inv.getArgument(0);
            f.setId(50L);
            f.setGuid("fil-50");
            return f;
        });

        FilamentResponse response = service.createFilament(new CreateFilamentRequest(
                "PLA Matte Preto",
                "Bambu Lab",
                MaterialType.PLA,
                "Preto",
                "#111827",
                new BigDecimal("1000.00"),
                null,
                new BigDecimal("135.00"),
                null,
                new BigDecimal("200.00"),
                18,
                "Drybox #1",
                "Rolo novo"
        ));

        assertEquals("fil-50", response.guid());
        assertFalse(response.lowStock());
        ArgumentCaptor<FilamentStockMovement> captor = ArgumentCaptor.forClass(FilamentStockMovement.class);
        verify(movementRepository).save(captor.capture());
        assertEquals(StockAdjustmentReason.PURCHASE, captor.getValue().getReason());
        assertEquals(new BigDecimal("1000.00"), captor.getValue().getDeltaGrams());
    }

    @Test
    void adjustStockRejectsNegativeBalanceAndAuditsValidChange() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        Filament filament = new Filament();
        filament.setId(50L);
        filament.setGuid("fil-50");
        filament.setCompanyId(10L);
        filament.setName("PETG Azul");
        filament.setBrand("eSun");
        filament.setMaterial(MaterialType.PETG);
        filament.setColorName("Azul");
        filament.setColorHex("#3b82f6");
        filament.setStock(new BigDecimal("120.00"));
        filament.setLowStockThresholdGrams(new BigDecimal("250.00"));
        filament.setStatus(StatusCd.ACTIVE);

        when(filamentRepository.findByGuidAndStatus("fil-50", StatusCd.ACTIVE)).thenReturn(Optional.of(filament));

        assertThrows(BadRequestException.class, () -> service.adjustStock(
                "fil-50",
                new AdjustFilamentStockRequest(new BigDecimal("-200.00"), StockAdjustmentReason.CALIBRATION, "Excesso")
        ));
        verify(movementRepository, never()).save(any());

        when(filamentRepository.save(any(Filament.class))).thenAnswer(inv -> inv.getArgument(0));
        FilamentResponse updated = service.adjustStock(
                "fil-50",
                new AdjustFilamentStockRequest(new BigDecimal("-50.00"), StockAdjustmentReason.CALIBRATION, "Torre temp")
        );

        assertEquals(new BigDecimal("70.00"), updated.stockGrams());
        assertTrue(updated.lowStock());
        verify(movementRepository).save(any(FilamentStockMovement.class));
    }

    @Test
    void listFilamentsFiltersLowStockAndConsumeFloorsAtZero() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        Filament ok = new Filament();
        ok.setId(1L);
        ok.setGuid("f-1");
        ok.setName("PLA Branco");
        ok.setMaterial(MaterialType.PLA);
        ok.setStock(new BigDecimal("800.00"));
        ok.setLowStockThresholdGrams(new BigDecimal("200.00"));

        Filament low = new Filament();
        low.setId(2L);
        low.setGuid("f-2");
        low.setName("PETG Cinza");
        low.setMaterial(MaterialType.PETG);
        low.setStock(new BigDecimal("90.00"));
        low.setLowStockThresholdGrams(new BigDecimal("200.00"));
        low.setStatus(StatusCd.ACTIVE);

        when(filamentRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of(ok, low));

        PageResponse<FilamentResponse> page = service.listFilaments(null, null, true, PageRequest.of(0, 10));
        assertEquals(1, page.totalElements());
        assertEquals("f-2", page.content().getFirst().guid());

        when(filamentRepository.findById(2L)).thenReturn(Optional.of(low));
        when(filamentRepository.save(any(Filament.class))).thenAnswer(inv -> inv.getArgument(0));

        service.consumeStockForPrint(2L, new BigDecimal("150.00"), StockAdjustmentReason.PRINT_CONSUMPTION, 90L, "Job");

        assertEquals(new BigDecimal("0.00"), low.getStock());
        ArgumentCaptor<FilamentStockMovement> movCaptor = ArgumentCaptor.forClass(FilamentStockMovement.class);
        verify(movementRepository).save(movCaptor.capture());
        assertEquals(new BigDecimal("-90.00"), movCaptor.getValue().getDeltaGrams());
        assertEquals(90L, movCaptor.getValue().getPrintQueueItemId());
    }
}
