package org.imprime.ai.api.service;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.admin.PatchFilamentCatalogRequest;
import org.imprime.ai.api.http.request.admin.UpsertFilamentCatalogRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.catalog.FilamentCatalogResponse;
import org.imprime.ai.api.model.FilamentCatalog;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.FilamentFinishType;
import org.imprime.ai.api.model.enums.HygroscopicLevel;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.SpoolType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.db.FilamentCatalogRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.service.impl.FilamentCatalogServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilamentCatalogServiceImplTest {

    @Mock
    private FilamentCatalogRepository filamentCatalogRepository;
    @Mock
    private FilamentRepository filamentRepository;
    @Mock
    private AdminAuditService adminAuditService;

    private FilamentCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FilamentCatalogServiceImpl(
                filamentCatalogRepository,
                filamentRepository,
                adminAuditService
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
    void listFilamentsShouldFilterByMaterialHexAndAms() {
        FilamentCatalog pla = buildFilament(1L, "fc-pla", "Bambu Lab", "PLA Matte", MaterialType.PLA, "Charcoal", "#1E293B", true);
        FilamentCatalog tpu = buildFilament(2L, "fc-tpu", "eSUN", "eTPU-95A", MaterialType.TPU, "Laranja", "#F97316", false);

        when(filamentCatalogRepository.findAllByStatusOrderByBrandAscLineNameAscColorNameAsc(StatusCd.ACTIVE))
                .thenReturn(List.of(pla, tpu));
        when(filamentRepository.countGroupedByFilamentCatalogId(StatusCd.ACTIVE))
                .thenReturn(List.<Object[]>of(new Object[]{1L, 12L}));

        PageResponse<FilamentCatalogResponse> result = service.listFilaments(
                "#1e29", MaterialType.PLA, null, null, true, null, null, null, null, false, PageRequest.of(0, 20)
        );

        assertEquals(1, result.content().size());
        assertEquals("fc-pla", result.content().getFirst().guid());
        assertEquals(12L, result.content().getFirst().activeInventoryCount());
    }

    @Test
    void createFilamentShouldRejectInvalidTemperatureRange() {
        UpsertFilamentCatalogRequest badTemp = new UpsertFilamentCatalogRequest(
                "Bambu Lab", "PLA Basic", MaterialType.PLA, "Basic",
                "Jade White", "#ffffff", null, FilamentFinishType.STANDARD,
                new BigDecimal("1.75"), new BigDecimal("1.24"), 1000, SpoolType.REUSABLE,
                240, 210, 220, // min > max!
                35, 65, 55,
                null, null, null, 50, 6,
                false, false, true, HygroscopicLevel.LOW, null,
                new BigDecimal("149.90"), "SKU-1", null, null, null, null, null
        );

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.createFilament(badTemp));
        assertEquals(MessageCd.INVALID_TEMPERATURE_RANGE, ex.getMessageCd());
    }

    @Test
    void createFilamentShouldRejectDuplicateActiveProfile() {
        UpsertFilamentCatalogRequest req = sampleRequest();
        when(filamentCatalogRepository.existsByBrandIgnoreCaseAndLineNameIgnoreCaseAndColorNameIgnoreCaseAndDiameterMmAndStatus(
                "3D Lab", "PLA Premium", "Azul Cobalto", new BigDecimal("1.75"), StatusCd.ACTIVE
        )).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.createFilament(req));
        assertEquals(MessageCd.FILAMENT_CATALOG_ALREADY_EXISTS, ex.getMessageCd());
    }

    @Test
    void createPatchAndDeleteFilamentShouldNormalizeHexAndAudit() {
        UpsertFilamentCatalogRequest req = sampleRequest();
        when(filamentCatalogRepository.existsBySlug(any())).thenReturn(false);
        when(filamentCatalogRepository.save(any(FilamentCatalog.class))).thenAnswer(inv -> {
            FilamentCatalog entity = inv.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(22L);
                entity.setGuid("fc-22");
                entity.setCreateDt(Instant.now());
            }
            return entity;
        });

        FilamentCatalogResponse created = service.createFilament(req);
        assertEquals("#2563EB", created.colorHex());
        assertEquals("3d-lab-pla-premium-azul-cobalto", created.slug());
        verify(adminAuditService).record(eq(AuditAction.FILAMENT_CATALOG_CREATED), eq("FILAMENT_CATALOG"), eq("fc-22"), any(), eq(null), any());

        FilamentCatalog stored = buildFilament(22L, "fc-22", "3D Lab", "PLA Premium", MaterialType.PLA, "Azul Cobalto", "#2563EB", true);
        when(filamentCatalogRepository.findByGuid("fc-22")).thenReturn(Optional.of(stored));
        when(filamentRepository.countByFilamentCatalogIdAndStatus(22L, StatusCd.ACTIVE)).thenReturn(3L);

        FilamentCatalogResponse patched = service.patchFilament("fc-22", new PatchFilamentCatalogRequest(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new BigDecimal("20.00"), new BigDecimal("0.0250"), null, null, null,
                null, null, null, null, null, new BigDecimal("119.90"),
                null, null, null, null, null, null, "Ajuste K e fluxo"
        ));
        assertEquals(new BigDecimal("20.00"), patched.maxVolumetricSpeedMm3S());
        assertEquals(new BigDecimal("0.0250"), patched.pressureAdvanceK());

        service.deleteFilament("fc-22", "Arquivado");
        assertEquals(StatusCd.DELETED, stored.getStatus());
        verify(adminAuditService).record(eq(AuditAction.FILAMENT_CATALOG_DELETED), eq("FILAMENT_CATALOG"), eq("fc-22"), eq("Arquivado"), any(), eq(null));
    }

    private FilamentCatalog buildFilament(
            Long id,
            String guid,
            String brand,
            String line,
            MaterialType material,
            String colorName,
            String hex,
            boolean amsCompatible
    ) {
        FilamentCatalog f = new FilamentCatalog();
        f.setId(id);
        f.setGuid(guid);
        f.setBrand(brand);
        f.setLineName(line);
        f.setSlug((brand + "-" + line + "-" + colorName).toLowerCase().replace(" ", "-"));
        f.setMaterial(material);
        f.setColorName(colorName);
        f.setColorHex(hex);
        f.setFinishType(FilamentFinishType.STANDARD);
        f.setDiameterMm(new BigDecimal("1.75"));
        f.setDensityGCm3(new BigDecimal("1.240"));
        f.setDefaultSpoolWeightG(1000);
        f.setSpoolType(SpoolType.PLASTIC);
        f.setNozzleTempMinC(190);
        f.setNozzleTempMaxC(225);
        f.setNozzleTempRecommendedC(210);
        f.setBedTempMinC(50);
        f.setBedTempMaxC(65);
        f.setBedTempRecommendedC(60);
        f.setAmsCompatible(amsCompatible);
        f.setRequiresEnclosure(false);
        f.setRequiresHardenedNozzle(false);
        f.setHygroscopicLevel(HygroscopicLevel.LOW);
        f.setStatus(StatusCd.ACTIVE);
        return f;
    }

    private UpsertFilamentCatalogRequest sampleRequest() {
        return new UpsertFilamentCatalogRequest(
                "3D Lab",
                "PLA Premium",
                MaterialType.PLA,
                "Standard",
                "Azul Cobalto",
                "#2563eb",
                null,
                FilamentFinishType.STANDARD,
                new BigDecimal("1.75"),
                new BigDecimal("1.240"),
                1000,
                SpoolType.PLASTIC,
                190,
                220,
                205,
                50,
                65,
                60,
                new BigDecimal("16.00"),
                new BigDecimal("0.0200"),
                new BigDecimal("2.50"),
                45,
                6,
                false,
                false,
                true,
                HygroscopicLevel.LOW,
                null,
                new BigDecimal("109.90"),
                "3DLAB-PLA-BLU",
                null,
                null,
                null,
                "Notas",
                "Cadastro teste"
        );
    }
}
