package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.AmsSlotRequest;
import org.imprime.ai.api.http.request.maker.CreatePrinterRequest;
import org.imprime.ai.api.http.request.maker.PrinterCommandRequest;
import org.imprime.ai.api.http.request.maker.UpdatePrinterRequest;
import org.imprime.ai.api.http.response.maker.PrinterResponse;
import org.imprime.ai.api.http.response.maker.RegenerateApiKeyResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.PrinterAmsSlot;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterCommandAction;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.repo.db.PrinterAmsSlotRepository;
import org.imprime.ai.api.service.impl.MakerPrinterServiceImpl;
import org.imprime.ai.api.util.HashUtil;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerPrinterServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private PrinterAmsSlotRepository amsSlotRepository;
    @Mock
    private FilamentRepository filamentRepository;
    @Mock
    private PrintQueueItemRepository printQueueItemRepository;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();

    @InjectMocks
    private MakerPrinterServiceImpl service;

    private Company company;
    private Asset printer;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");

        printer = new Asset();
        printer.setId(100L);
        printer.setGuid("prt-100");
        printer.setCompanyId(10L);
        printer.setName("X1C Principal");
        printer.setModel("Bambu Lab X1 Carbon");
        printer.setMake("Bambu");
        printer.setManufactureYear((short) 2025);
        printer.setPrinterStatus(PrinterStatus.ONLINE);
        printer.setStatus(StatusCd.ACTIVE);

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
    }

    @Test
    void createPrinterAppliesOperationalDefaults() {
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> {
            Asset saved = inv.getArgument(0);
            saved.setId(101L);
            saved.setGuid("prt-101");
            return saved;
        });
        when(amsSlotRepository.findAllByAssetIdAndStatusOrderBySlotNumberAsc(101L, StatusCd.ACTIVE)).thenReturn(List.of());
        when(printQueueItemRepository.findAllByAssetIdAndJobStatusInAndStatus(eq(101L), any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of());

        PrinterResponse response = service.createPrinter(new CreatePrinterRequest(
                "P1S #2", "Bambu Lab P1S", null, null, null, "192.168.1.90", null, null, null
        ));

        assertEquals("prt-101", response.guid());
        assertEquals(PrinterStatus.ONLINE, response.status());
        assertEquals(256, response.buildVolume().xMm());
        assertEquals(new BigDecimal("0.400"), response.nozzleDiameterMm());
        assertEquals(300, response.powerWatts());
        assertFalse(response.config().apiKeyConfigured());
        assertNull(response.purchasePrice());
        assertNull(response.lifespanHours());
        assertNull(response.hourlyDepreciationRate());
    }

    @Test
    void regenerateApiKeyStoresOnlySha256HashAndReturnsRawOnce() {
        when(assetRepository.findByGuidAndStatus("prt-100", StatusCd.ACTIVE)).thenReturn(Optional.of(printer));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));

        RegenerateApiKeyResponse response = service.regenerateApiKey("prt-100");

        assertTrue(response.apiKey().startsWith("iak_live_"));
        assertTrue(response.apiKeyPrefix().endsWith("****"));
        assertEquals(HashUtil.sha256(response.apiKey()), printer.getApiKeyHash());
        assertNotEquals(response.apiKey(), printer.getApiKeyHash());
    }

    @Test
    void updatePrinterSyncsAmsSlotsAndRejectsForeignFilament() {
        Filament foreignFilament = new Filament();
        foreignFilament.setId(55L);
        foreignFilament.setGuid("fil-55");
        foreignFilament.setCompanyId(999L);
        foreignFilament.setStatus(StatusCd.ACTIVE);

        when(assetRepository.findByGuidAndStatus("prt-100", StatusCd.ACTIVE)).thenReturn(Optional.of(printer));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(filamentRepository.findByGuidAndStatus("fil-55", StatusCd.ACTIVE)).thenReturn(Optional.of(foreignFilament));

        assertThrows(ForbiddenException.class, () -> service.updatePrinter(
                "prt-100",
                new UpdatePrinterRequest(null, null, null, null, null, null, null,
                        List.of(new AmsSlotRequest(1, "fil-55", "Slot A1")), null,
                        null, null, null, null, null, null)
        ));

        Filament ownedFilament = new Filament();
        ownedFilament.setId(56L);
        ownedFilament.setGuid("fil-56");
        ownedFilament.setCompanyId(10L);
        ownedFilament.setStatus(StatusCd.ACTIVE);

        when(filamentRepository.findByGuidAndStatus("fil-56", StatusCd.ACTIVE)).thenReturn(Optional.of(ownedFilament));
        when(amsSlotRepository.findAllByAssetIdAndStatusOrderBySlotNumberAsc(100L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(amsSlotRepository.save(any(PrinterAmsSlot.class))).thenAnswer(inv -> inv.getArgument(0));
        when(printQueueItemRepository.findAllByAssetIdAndJobStatusInAndStatus(eq(100L), any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of());

        service.updatePrinter(
                "prt-100",
                new UpdatePrinterRequest(null, null, null, null, null, null, null,
                        List.of(new AmsSlotRequest(1, "fil-56", "Slot A1")), null,
                        null, null, null, null, null, null)
        );
        verify(amsSlotRepository).save(any(PrinterAmsSlot.class));
    }

    @Test
    void sendCommandPauseResumeStopAndDeleteBusyCheckWorkCorrectly() {
        printer.setPrinterStatus(PrinterStatus.PRINTING);
        PrintQueueItem runningJob = new PrintQueueItem();
        runningJob.setId(700L);
        runningJob.setGuid("job-700");
        runningJob.setAssetId(100L);
        runningJob.setJobStatus(PrintJobStatus.PRINTING);

        when(assetRepository.findByGuidAndStatus("prt-100", StatusCd.ACTIVE)).thenReturn(Optional.of(printer));
        when(printQueueItemRepository.existsByAssetIdAndJobStatusInAndStatus(eq(100L), any(), eq(StatusCd.ACTIVE)))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.deletePrinter("prt-100"));

        when(printQueueItemRepository.findAllByAssetIdAndJobStatusInAndStatus(eq(100L), any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of(runningJob));
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(amsSlotRepository.findAllByAssetIdAndStatusOrderBySlotNumberAsc(100L, StatusCd.ACTIVE)).thenReturn(List.of());

        PrinterResponse paused = service.sendCommand("prt-100", new PrinterCommandRequest(PrinterCommandAction.PAUSE));
        assertEquals(PrinterStatus.PAUSED, paused.status());
        assertEquals(PrintJobStatus.PAUSED, runningJob.getJobStatus());

        PrinterResponse stopped = service.sendCommand("prt-100", new PrinterCommandRequest(PrinterCommandAction.STOP));
        assertEquals(PrinterStatus.ONLINE, stopped.status());
        assertEquals(PrintJobStatus.CANCELLED, runningJob.getJobStatus());
    }

    @Test
    void createAndUpdatePrinterPersistAndClearOptionalFinancialFields() {
        when(assetRepository.save(any(Asset.class))).thenAnswer(inv -> {
            Asset saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(102L);
                saved.setGuid("prt-102");
            }
            return saved;
        });
        when(amsSlotRepository.findAllByAssetIdAndStatusOrderBySlotNumberAsc(anyLong(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of());
        when(printQueueItemRepository.findAllByAssetIdAndJobStatusInAndStatus(anyLong(), any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of());

        PrinterResponse created = service.createPrinter(new CreatePrinterRequest(
                "A1 Mini", "Bambu Lab A1 Mini", null, null, 220, null,
                new BigDecimal("2199.90"), 3000, new BigDecimal("1.45")
        ));

        assertEquals(new BigDecimal("2199.90"), created.purchasePrice());
        assertEquals(3000, created.lifespanHours());
        assertEquals(new BigDecimal("1.45"), created.hourlyDepreciationRate());

        printer.setPurchasePrice(new BigDecimal("2199.90"));
        printer.setLifespanHours(3000);
        printer.setHourlyDepreciationRate(new BigDecimal("1.45"));
        when(assetRepository.findByGuidAndStatus("prt-100", StatusCd.ACTIVE)).thenReturn(Optional.of(printer));

        PrinterResponse updated = service.updatePrinter(
                "prt-100",
                new UpdatePrinterRequest(
                        null, null, null, null, null, null, null, null, null,
                        new BigDecimal("2500.00"), null, null,
                        false, true, true
                )
        );

        assertEquals(new BigDecimal("2500.00"), updated.purchasePrice());
        assertNull(updated.lifespanHours());
        assertNull(updated.hourlyDepreciationRate());
    }
}
