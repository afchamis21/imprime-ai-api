package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.QueueNextPlateRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProjectRequest;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerProject;
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
        when(projectRepository.findByGuidAndStatus("prj-40", StatusCd.ACTIVE)).thenReturn(Optional.of(project));

        assertThrows(BadRequestException.class, () -> service.updateProject(
                "prj-40",
                new UpdateMakerProjectRequest(null, null, null, null, 5, null, null)
        ));
    }

    @Test
    void queueNextPlateMovesPlanningToInProgressAndRejectsWhenAlreadyCompleted() {
        Asset printer = new Asset();
        printer.setId(100L);
        printer.setGuid("prt-100");
        printer.setCompanyId(10L);
        printer.setPrinterStatus(PrinterStatus.ONLINE);

        when(projectRepository.findByGuidAndStatus("prj-40", StatusCd.ACTIVE)).thenReturn(Optional.of(project));
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
