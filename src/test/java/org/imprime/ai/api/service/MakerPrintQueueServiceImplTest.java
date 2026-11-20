package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.PrintJobTransitionRequest;
import org.imprime.ai.api.http.request.maker.ReorderPrintQueueRequest;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.MakerProject;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.enums.PrintJobAction;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.ProjectStatus;
import org.imprime.ai.api.model.enums.QueuePriority;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.StockAdjustmentReason;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerArchiveFileRepository;
import org.imprime.ai.api.repo.db.MakerProjectRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.service.impl.MakerFilamentServiceImpl;
import org.imprime.ai.api.service.impl.MakerPrintQueueServiceImpl;
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
class MakerPrintQueueServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private PrintQueueItemRepository printQueueItemRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private MakerArchiveFileRepository archiveFileRepository;
    @Mock
    private FilamentRepository filamentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private MakerProjectRepository projectRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private MakerFilamentServiceImpl makerFilamentService;
    @Spy
    private JsonCodec jsonCodec = new JsonCodec();

    @InjectMocks
    private MakerPrintQueueServiceImpl service;

    private Company company;
    private Asset printer;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");
        company.setPrintsDone(5);

        printer = new Asset();
        printer.setId(100L);
        printer.setGuid("prt-100");
        printer.setCompanyId(10L);
        printer.setName("X1C");
        printer.setPrinterStatus(PrinterStatus.ONLINE);
        printer.setStatus(StatusCd.ACTIVE);

        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
    }

    @Test
    void startJobFailsWhenPrinterBusyAndCompleteJobConsumesFilamentAndAdvancesProject() {
        PrintQueueItem job = new PrintQueueItem();
        job.setId(200L);
        job.setGuid("job-200");
        job.setCompanyId(10L);
        job.setAssetId(100L);
        job.setFilamentId(50L);
        job.setMakerProjectId(300L);
        job.setTitle("Capacete Mesa 1");
        job.setEstimatedWeightGrams(new BigDecimal("180.00"));
        job.setEstimatedTimeMinutes(120);
        job.setJobStatus(PrintJobStatus.QUEUED);
        job.setPriority(QueuePriority.HIGH);
        job.setCopies(1);
        job.setCompletedCopies(0);
        job.setStatus(StatusCd.ACTIVE);

        printer.setPrinterStatus(PrinterStatus.PRINTING);
        when(printQueueItemRepository.findByGuidAndStatus("job-200", StatusCd.ACTIVE)).thenReturn(Optional.of(job));
        when(assetRepository.findById(100L)).thenReturn(Optional.of(printer));

        assertThrows(ConflictException.class, () ->
                service.transitionJob("job-200", new PrintJobTransitionRequest(PrintJobAction.START, null, null))
        );

        printer.setPrinterStatus(PrinterStatus.ONLINE);
        job.setJobStatus(PrintJobStatus.PRINTING);
        MakerProject project = new MakerProject();
        project.setId(300L);
        project.setGuid("prj-300");
        project.setCompanyId(10L);
        project.setTotalPlates(2);
        project.setCompletedPlates(1);
        project.setProjectStatus(ProjectStatus.IN_PROGRESS);
        project.setStatus(StatusCd.ACTIVE);

        when(printQueueItemRepository.findAllByAssetIdAndJobStatusInAndStatus(eq(100L), any(), eq(StatusCd.ACTIVE)))
                .thenReturn(List.of());
        when(projectRepository.findById(300L)).thenReturn(Optional.of(project));
        when(printQueueItemRepository.save(any(PrintQueueItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.findAllByIdIn(any())).thenReturn(List.of(printer));
        when(projectRepository.findAllByIdIn(any())).thenReturn(List.of(project));

        PrintQueueItemResponse completed = service.transitionJob(
                "job-200", new PrintJobTransitionRequest(PrintJobAction.COMPLETE, null, null)
        );

        assertEquals(PrintJobStatus.COMPLETED, completed.status());
        assertEquals(100, completed.progressPercent());
        assertEquals(PrinterStatus.ONLINE, printer.getPrinterStatus());
        assertEquals(6, company.getPrintsDone());
        assertEquals(2, project.getCompletedPlates());
        assertEquals(ProjectStatus.COMPLETED, project.getProjectStatus());
        verify(makerFilamentService).consumeStockForPrint(
                eq(50L), eq(new BigDecimal("180.00")), eq(StockAdjustmentReason.PRINT_CONSUMPTION), eq(200L), anyString()
        );
    }

    @Test
    void reorderQueueReindexesPositionsAtomicallyAndRejectsDuplicates() {
        assertThrows(BadRequestException.class, () ->
                service.reorderQueue(new ReorderPrintQueueRequest(List.of("job-1", "job-1")))
        );

        PrintQueueItem j1 = new PrintQueueItem();
        j1.setId(1L);
        j1.setGuid("job-1");
        j1.setAssetId(100L);
        j1.setPosition(1);
        j1.setJobStatus(PrintJobStatus.QUEUED);
        j1.setPriority(QueuePriority.NORMAL);

        PrintQueueItem j2 = new PrintQueueItem();
        j2.setId(2L);
        j2.setGuid("job-2");
        j2.setAssetId(100L);
        j2.setPosition(2);
        j2.setJobStatus(PrintJobStatus.QUEUED);
        j2.setPriority(QueuePriority.NORMAL);

        when(printQueueItemRepository.findAllByCompanyIdAndGuidInAndStatus(10L, List.of("job-2", "job-1"), StatusCd.ACTIVE))
                .thenReturn(List.of(j1, j2));
        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(j1, j2));
        when(printQueueItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.findAllByIdIn(any())).thenReturn(List.of(printer));

        List<PrintQueueItemResponse> reordered = service.reorderQueue(new ReorderPrintQueueRequest(List.of("job-2", "job-1")));

        assertEquals("job-2", reordered.get(0).guid());
        assertEquals(1, reordered.get(0).position());
        assertEquals("job-1", reordered.get(1).guid());
        assertEquals(2, reordered.get(1).position());
    }
}
