package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.PatchPrinterCatalogRequest;
import org.imprime.ai.api.http.request.admin.UpsertPrinterCatalogRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.catalog.PrinterCatalogResponse;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.ExtruderType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.PrinterConnectivity;
import org.imprime.ai.api.model.enums.PrinterKinematics;
import org.imprime.ai.api.model.enums.PrinterTechnology;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.service.impl.PrinterCatalogServiceImpl;
import org.imprime.ai.api.util.JsonCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrinterCatalogServiceImplTest {

    @Mock
    private PrinterCatalogRepository printerCatalogRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AdminAuditService adminAuditService;

    private PrinterCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PrinterCatalogServiceImpl(
                printerCatalogRepository,
                assetRepository,
                adminAuditService,
                new JsonCodec()
        );
        User admin = new User();
        admin.setId(1L);
        admin.setGuid("admin-guid");
        admin.setRole(UserRole.ADMIN);
        ServiceContext.getContext().setUser(admin);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void listPrintersShouldFilterByMulticolorEnclosedAndMaterialAndReturnFleetCount() {
        PrinterCatalog a1 = buildPrinter(1L, "pc-a1", "Bambu Lab", "A1", false, true, Set.of(MaterialType.PLA, MaterialType.PETG));
        PrinterCatalog p1s = buildPrinter(2L, "pc-p1s", "Bambu Lab", "P1S", true, true, Set.of(MaterialType.PLA, MaterialType.ABS, MaterialType.ASA));

        when(printerCatalogRepository.findAllByStatusOrderByBrandAscModelNameAsc(StatusCd.ACTIVE))
                .thenReturn(List.of(a1, p1s));
        when(assetRepository.countGroupedByPrinterCatalogId(StatusCd.ACTIVE))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 8L}, new Object[]{2L, 3L}));

        PageResponse<PrinterCatalogResponse> page = service.listPrinters(
                "Bambu", null, null, true, true, MaterialType.ABS,
                new BigDecimal("250"), null, null, false, PageRequest.of(0, 20)
        );

        assertEquals(1, page.content().size());
        PrinterCatalogResponse item = page.content().getFirst();
        assertEquals("pc-p1s", item.guid());
        assertEquals(3L, item.activeMakersFleetCount());
        assertTrue(item.isEnclosed());
        assertTrue(item.supportsMulticolor());
    }

    @Test
    void createPrinterShouldRejectDuplicateBrandAndModel() {
        when(printerCatalogRepository.existsByBrandIgnoreCaseAndModelNameIgnoreCaseAndStatus(
                "Bambu Lab", "A1", StatusCd.ACTIVE))
                .thenReturn(true);

        UpsertPrinterCatalogRequest req = sampleUpsertRequest("Bambu Lab", "A1", new BigDecimal("0.08"), new BigDecimal("0.28"));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.createPrinter(req));
        assertEquals(MessageCd.PRINTER_CATALOG_ALREADY_EXISTS, ex.getMessageCd());
    }

    @Test
    void createPrinterShouldValidateLayerHeightAndPersistWithAudit() {
        UpsertPrinterCatalogRequest invalid = sampleUpsertRequest("Creality", "K1C", new BigDecimal("0.30"), new BigDecimal("0.10"));
        BadRequestException badEx = assertThrows(BadRequestException.class, () -> service.createPrinter(invalid));
        assertEquals(MessageCd.VALIDATION_ERROR, badEx.getMessageCd());

        UpsertPrinterCatalogRequest valid = sampleUpsertRequest("Bambu Lab", "A1", new BigDecimal("0.08"), new BigDecimal("0.28"));
        when(printerCatalogRepository.existsBySlug(any())).thenReturn(false);
        when(printerCatalogRepository.save(any(PrinterCatalog.class))).thenAnswer(inv -> {
            PrinterCatalog entity = inv.getArgument(0);
            entity.setId(10L);
            entity.setGuid("pc-created-10");
            entity.setCreateDt(Instant.now());
            return entity;
        });

        PrinterCatalogResponse created = service.createPrinter(valid);

        assertEquals("Bambu Lab A1", created.displayName());
        assertEquals("bambu-lab-a1", created.slug());
        assertEquals(List.of(PrinterConnectivity.WIFI, PrinterConnectivity.CLOUD), created.connectivityMethods());
        assertTrue(created.supportedMaterials().contains(MaterialType.PLA));
        verify(adminAuditService).record(
                eq(AuditAction.PRINTER_CATALOG_CREATED),
                eq("PRINTER_CATALOG"),
                eq("pc-created-10"),
                any(),
                eq(null),
                any()
        );
    }

    @Test
    void patchAndDeletePrinterShouldUpdateAndSoftDelete() {
        PrinterCatalog existing = buildPrinter(5L, "pc-k1", "Creality", "K1C", true, false, Set.of(MaterialType.PLA, MaterialType.ABS));
        when(printerCatalogRepository.findByGuid("pc-k1")).thenReturn(Optional.of(existing));
        when(assetRepository.countByPrinterCatalogIdAndStatus(5L, StatusCd.ACTIVE)).thenReturn(4L);
        when(printerCatalogRepository.save(any(PrinterCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        PatchPrinterCatalogRequest patch = new PatchPrinterCatalogRequest(
                null, null, null, null, null, null, 320, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                true, "CFS", 8, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, "Upgrade CFS"
        );
        PrinterCatalogResponse updated = service.patchPrinter("pc-k1", patch);

        assertEquals(320, updated.maxNozzleTempC());
        assertTrue(updated.supportsMulticolor());
        assertEquals(8, updated.maxColorSlots());
        assertEquals("CFS", updated.multicolorSystemName());

        service.deletePrinter("pc-k1", "Descontinuada");
        assertEquals(StatusCd.DELETED, existing.getStatus());
        verify(adminAuditService).record(eq(AuditAction.PRINTER_CATALOG_DELETED), eq("PRINTER_CATALOG"), eq("pc-k1"), eq("Descontinuada"), any(), eq(null));

        assertThrows(NotFoundException.class, () -> service.getPrinterByGuid("pc-k1", true));
    }

    private PrinterCatalog buildPrinter(
            Long id,
            String guid,
            String brand,
            String modelName,
            boolean enclosed,
            boolean multicolor,
            Set<MaterialType> materials
    ) {
        PrinterCatalog p = new PrinterCatalog();
        p.setId(id);
        p.setGuid(guid);
        p.setBrand(brand);
        p.setModelName(modelName);
        p.setSlug((brand + "-" + modelName).toLowerCase().replace(" ", "-"));
        p.setBuildVolumeXMm(new BigDecimal("256.00"));
        p.setBuildVolumeYMm(new BigDecimal("256.00"));
        p.setBuildVolumeZMm(new BigDecimal("256.00"));
        p.setIsEnclosed(enclosed);
        p.setMaxNozzleTempC(300);
        p.setMaxBedTempC(100);
        p.setDefaultNozzleMm(new BigDecimal("0.400"));
        p.setFilamentDiameterMm(new BigDecimal("1.75"));
        p.setMaxPrintSpeedMmS(500);
        p.setMaxPowerWatts110v(350);
        p.setMaxPowerWatts220v(1000);
        p.setInputVoltage("BIVOLT_110_220V");
        p.setConnectivityMethodsJson("[\"WIFI\",\"CLOUD\"]");
        p.setWeightKg(new BigDecimal("12.50"));
        p.setTechnology(PrinterTechnology.FDM);
        p.setKinematics(enclosed ? PrinterKinematics.COREXY : PrinterKinematics.BED_SLINGER);
        p.setExtruderType(ExtruderType.DIRECT_DRIVE);
        p.setSupportsMulticolor(multicolor);
        p.setMaxColorSlots(multicolor ? 4 : 1);
        p.setSupportedNozzleSizesJson("[0.2,0.4,0.6]");
        p.setSupportedMaterials(new LinkedHashSet<>(materials));
        p.setStatus(StatusCd.ACTIVE);
        return p;
    }

    private UpsertPrinterCatalogRequest sampleUpsertRequest(
            String brand,
            String modelName,
            BigDecimal minLayer,
            BigDecimal maxLayer
    ) {
        return new UpsertPrinterCatalogRequest(
                brand,
                modelName,
                new BigDecimal("256"),
                new BigDecimal("256"),
                new BigDecimal("256"),
                false,
                300,
                100,
                new BigDecimal("0.4"),
                new BigDecimal("1.75"),
                500,
                350,
                1300,
                "BIVOLT_110_220V",
                List.of(PrinterConnectivity.WIFI, PrinterConnectivity.CLOUD),
                new BigDecimal("8.3"),
                List.of(MaterialType.PLA, MaterialType.PETG),
                PrinterTechnology.FDM,
                PrinterKinematics.BED_SLINGER,
                ExtruderType.DIRECT_DRIVE,
                true,
                "AMS Lite",
                4,
                110,
                false,
                null,
                minLayer,
                maxLayer,
                10000,
                List.of(new BigDecimal("0.2"), new BigDecimal("0.4")),
                false,
                true,
                true,
                true,
                false,
                2023,
                new BigDecimal("3490.00"),
                null,
                null,
                "Notas",
                "Teste"
        );
    }
}
