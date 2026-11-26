package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.ProjectFilamentUsageRequest;
import org.imprime.ai.api.http.request.maker.QueueNextPlateRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProjectRequest;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerProject;
import org.imprime.ai.api.model.MakerProjectBomItem;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.ProjectStatus;
import org.imprime.ai.api.model.enums.QueuePriority;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerArchiveFileRepository;
import org.imprime.ai.api.repo.db.MakerProjectBomItemRepository;
import org.imprime.ai.api.repo.db.MakerProjectRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.impl.MakerPrintQueueServiceImpl;
import org.imprime.ai.api.service.impl.MakerProjectServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MakerProjectServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private MakerProjectRepository projectRepository;
    @Mock
    private MakerProjectBomItemRepository bomItemRepository;
    @Mock
    private MakerArchiveFileRepository archiveFileRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private FilamentRepository filamentRepository;
    @Mock
    private MakerPrintQueueServiceImpl printQueueService;

    @InjectMocks
    private MakerProjectServiceImpl service;

    private Company company;
    private MakerProject project;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");

        project = new MakerProject();
        project.setId(40L);
        project.setGuid("prj-40");
        project.setCompanyId(10L);
        project.setName("Maquete Torre");
        project.setProjectStatus(ProjectStatus.PLANNING);
        project.setTotalPlates(3);
        project.setCompletedPlates(0);
        project.setTotalParts(10);
        project.setCompletedParts(0);
        project.setPrimaryMaterial(MaterialType.PLA);
        project.setEstimatedWeightGrams(new BigDecimal("300.00"));
        project.setEstimatedHours(new BigDecimal("6.00"));
        project.setStatus(StatusCd.ACTIVE);

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
    }

    @Test
    void updateProjectRejectsCompletedExceedingTotals() {
        when(projectRepository.findForUpdateByGuidAndStatus("prj-40", StatusCd.ACTIVE)).thenReturn(Optional.of(project));

        assertThrows(BadRequestException.class, () -> service.updateProject(
                "prj-40",
                new UpdateMakerProjectRequest(null, null, null, null, 5, null, null,
                        null, null, null, null, null, null, null, null, null, null, null)
        ));
    }

    @Test
    void updateProjectEditsTotalsAndCostsAndTracksBothPrintCounters() {
        when(projectRepository.findForUpdateByGuidAndStatus("prj-40", StatusCd.ACTIVE)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(archiveFileRepository.findAllByMakerProjectIdAndStatusOrderByCreateDtAsc(40L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(bomItemRepository.findAllByMakerProjectIdAndStatusOrderByIdAsc(40L, StatusCd.ACTIVE))
                .thenReturn(List.of());

        var edited = service.updateProject("prj-40", new UpdateMakerProjectRequest(
                "Novo nome", "Descrição atualizada", null, null, 4, 8, "",
                "Cliente", 4, 9, null, null, new BigDecimal("250.00"), new BigDecimal("72.50"),
                MaterialType.PETG, "#aabbcc", null, null
        ));
        assertEquals("Novo nome", edited.name());
        assertEquals(4, edited.completedPlates());
        assertEquals(9, edited.totalParts());
        assertEquals(new BigDecimal("72.50"), edited.estimatedCost());
        assertEquals(ProjectStatus.IN_PROGRESS, edited.status());

        var completed = service.updateProject("prj-40", new UpdateMakerProjectRequest(
                null, null, null, null, null, 9, null,
                null, null, null, null, null, null, null, null, null, null, null
        ));
        assertEquals(ProjectStatus.COMPLETED, completed.status());

        var reopened = service.updateProject("prj-40", new UpdateMakerProjectRequest(
                null, null, null, null, 3, null, null,
                null, null, null, null, null, null, null, null, null, null, null
        ));
        assertEquals(ProjectStatus.IN_PROGRESS, reopened.status());
    }

    @Test
    void recordsDifferentFilamentsAndKeepsTheirOriginalPricePerKg() {
        Filament pla = new Filament();
        pla.setCompanyId(company.getId());
        pla.setName("PLA roxo");
        pla.setCostPerKg(new BigDecimal("80.00"));
        Filament petg = new Filament();
        petg.setCompanyId(company.getId());
        petg.setName("PETG azul");
        petg.setCostPerKg(new BigDecimal("110.00"));

        when(projectRepository.findForUpdateByGuidAndStatus("prj-40", StatusCd.ACTIVE)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(filamentRepository.findByGuidAndStatus("pla", StatusCd.ACTIVE)).thenReturn(Optional.of(pla));
        when(filamentRepository.findByGuidAndStatus("petg", StatusCd.ACTIVE)).thenReturn(Optional.of(petg));

        var initial = service.updateProject("prj-40", filamentUpdate(List.of(
                new ProjectFilamentUsageRequest("pla", new BigDecimal("200.00"), null),
                new ProjectFilamentUsageRequest("petg", new BigDecimal("50.00"), null)
        )));
        assertEquals(new BigDecimal("21.50"), initial.filamentCost());
        assertEquals(2, initial.filamentUsage().size());

        pla.setCostPerKg(new BigDecimal("99.00"));
        var revised = service.updateProject("prj-40", filamentUpdate(List.of(
                new ProjectFilamentUsageRequest("pla", new BigDecimal("250.00"), null)
        )));
        assertEquals(new BigDecimal("20.00"), revised.filamentCost());
        verify(filamentRepository, times(1)).findByGuidAndStatus("pla", StatusCd.ACTIVE);
    }

    @Test
    void calculatesPartialPlateAndRecalculatesProjectCostFromFilamentAndBom() {
        Filament pla = new Filament();
        pla.setCompanyId(company.getId());
        pla.setName("PLA roxo");
        pla.setCostPerKg(new BigDecimal("80.00"));
        MakerProjectBomItem bolt = new MakerProjectBomItem();
        bolt.setName("Parafuso");
        bolt.setQuantity(new BigDecimal("10"));
        bolt.setUnitCost(new BigDecimal("0.25"));

        when(projectRepository.findForUpdateByGuidAndStatus("prj-40", StatusCd.ACTIVE)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(filamentRepository.findByGuidAndStatus("pla", StatusCd.ACTIVE)).thenReturn(Optional.of(pla));
        when(bomItemRepository.findAllByMakerProjectIdAndStatusOrderByIdAsc(40L, StatusCd.ACTIVE))
                .thenReturn(List.of(bolt));

        var initial = service.updateProject("prj-40", new UpdateMakerProjectRequest(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                List.of(new ProjectFilamentUsageRequest("pla", null, new BigDecimal("50.00"))), 3));
        assertEquals(4, initial.totalPlates());
        assertEquals(3, initial.partsPerPlate());
        assertEquals(new BigDecimal("200.00"), initial.filamentUsage().getFirst().grams());
        assertEquals(new BigDecimal("16.00"), initial.filamentCost());
        assertEquals(new BigDecimal("18.50"), initial.estimatedCost());
        assertEquals(new BigDecimal("400.00"), initial.estimatedWeightGrams());

        var revised = service.updateProject("prj-40", new UpdateMakerProjectRequest(
                null, null, null, null, null, null, null,
                null, null, 6, null, null, null, null, null, null, null, null));
        assertEquals(2, revised.totalPlates());
        assertEquals(new BigDecimal("100.00"), revised.filamentUsage().getFirst().grams());
        assertEquals(new BigDecimal("10.50"), revised.estimatedCost());
        assertEquals(new BigDecimal("4.00"), revised.estimatedHours());
    }

    @Test
    void calculatesPrintedPartsFromPlatesAndHonorsManualCorrections() {
        project.setPartsPerPlate(4);
        when(projectRepository.findForUpdateByGuidAndStatus("prj-40", StatusCd.ACTIVE)).thenReturn(Optional.of(project));
        when(projectRepository.save(project)).thenReturn(project);

        var automatic = service.updateProject("prj-40", progressUpdate(2, null));
        assertEquals(8, automatic.completedParts());
        var corrected = service.updateProject("prj-40", progressUpdate(3, 9));
        assertEquals(9, corrected.completedParts());
        assertEquals(ProjectStatus.IN_PROGRESS, corrected.status());

        var completed = service.updateProject("prj-40", progressUpdate(3, null));
        assertEquals(10, completed.completedParts());
        assertEquals(ProjectStatus.COMPLETED, completed.status());
    }

    private UpdateMakerProjectRequest progressUpdate(int plates, Integer parts) {
        return new UpdateMakerProjectRequest(null, null, null, null, plates, parts, null,
                null, null, null, null, null, null, null, null, null, null, null);
    }

    private UpdateMakerProjectRequest filamentUpdate(List<ProjectFilamentUsageRequest> usage) {
        return new UpdateMakerProjectRequest(null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, usage, null);
    }

    @Test
    void queueNextPlateMovesPlanningToInProgressAndRejectsWhenAlreadyCompleted() {
        Asset printer = new Asset();
        printer.setId(100L);
        printer.setGuid("prt-100");
        printer.setCompanyId(10L);
        printer.setPrinterStatus(PrinterStatus.ONLINE);

        when(projectRepository.findForUpdateByGuidAndStatus("prj-40", StatusCd.ACTIVE)).thenReturn(Optional.of(project));
        when(assetRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of(printer));
        when(archiveFileRepository.findAllByMakerProjectIdAndStatusOrderByCreateDtAsc(40L, StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(printQueueService.enqueueBuiltJob(
                eq(company), eq(printer), isNull(), isNull(), isNull(), eq(project),
                eq("Maquete Torre — Mesa 1/3"), isNull(), eq(MaterialType.PLA), any(), any(),
                eq(new BigDecimal("100.00")), eq(120), eq(QueuePriority.HIGH), eq(1), any(), any()
        )).thenReturn(new PrintQueueItemResponse(
                "job-plate-1", 1, "Maquete Torre — Mesa 1/3", null, null, null,
                "prt-100", "X1C", null, MaterialType.PLA, null, null,
                new BigDecimal("100.00"), 120, 0, PrintJobStatus.QUEUED, QueuePriority.HIGH, 1, 0,
                null, null, "prj-40", null, null, null, Instant.now(), null, null, null
        ));

        PrintQueueItemResponse enqueued = service.queueNextPlate("prj-40", new QueueNextPlateRequest(null, null));

        assertEquals("job-plate-1", enqueued.guid());
        assertEquals(ProjectStatus.IN_PROGRESS, project.getProjectStatus());
        verify(projectRepository).save(project);

        project.setCompletedPlates(3);
        project.setProjectStatus(ProjectStatus.COMPLETED);
        assertThrows(ConflictException.class, () -> service.queueNextPlate("prj-40", null));
    }

    @Test
    void listProjectsHandlesOptionalOrderNullSafely() {
        when(projectRepository.findAllByCompanyIdAndStatus(10L, StatusCd.ACTIVE)).thenReturn(List.of(project));
        when(bomItemRepository.findAllByMakerProjectIdInAndStatusOrderByIdAsc(List.of(40L), StatusCd.ACTIVE))
                .thenReturn(List.of());
        when(archiveFileRepository.findAllByMakerProjectIdInAndStatus(List.of(40L), StatusCd.ACTIVE))
                .thenReturn(List.of());

        var result = service.listProjects(null, null, PageRequest.of(0, 10));

        assertEquals(1, result.totalElements());
        assertNull(result.content().getFirst().orderGuid());
        assertEquals("Maquete Torre", result.content().getFirst().name());
    }
}
