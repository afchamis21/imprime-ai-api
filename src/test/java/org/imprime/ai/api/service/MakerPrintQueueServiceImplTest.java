package org.imprime.ai.api.service;

import org.imprime.ai.api.http.request.maker.PrintJobTransitionRequest;
import org.imprime.ai.api.http.request.maker.ReorderPrintQueueRequest;
import org.imprime.ai.api.http.request.maker.UpdatePrintQueueItemRequest;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private MakerOrderSupport makerOrderSupport;

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
    void listQueueSyncsOrdersInProductionWithoutPrinterAndDeadlinePriority() {
        Order order = new Order();
        order.setId(900L);
        order.setGuid("ord-900");
        order.setOrderCode("PED-900");
        order.setBuyerId(77L);
        order.setVendorId(10L);
        order.setCreateDt(Instant.now().minus(Duration.ofDays(4)));

        Order cancelledOrder = new Order();
        cancelledOrder.setId(901L);
        cancelledOrder.setGuid("ord-901");
        cancelledOrder.setVendorId(10L);

        PrintQueueItem cancelledJob = new PrintQueueItem();
        cancelledJob.setId(5L);
        cancelledJob.setGuid("job-5");
        cancelledJob.setCompanyId(10L);
        cancelledJob.setOrderId(901L);
        cancelledJob.setJobStatus(PrintJobStatus.QUEUED);

        when(orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(order, cancelledOrder));
        when(makerOrderSupport.latestStatuses(any())).thenReturn(Map.of(
                900L, OrderStatusCd.IN_PRODUCTION,
                901L, OrderStatusCd.CANCELLED
        ));
        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(cancelledJob));
        Instant due = Instant.now().plus(Duration.ofHours(12));
        when(makerOrderSupport.dueDates(any(), eq(company))).thenReturn(Map.of(900L, due));
        when(makerOrderSupport.buyerNames(any())).thenReturn(Map.of(900L, "Ana Souza"));
        when(makerOrderSupport.resolveFiles(any())).thenReturn(Map.of());
        when(printQueueItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.listQueue(null, null, null, null, PageRequest.of(0, 20));

        assertEquals(PrintJobStatus.CANCELLED, cancelledJob.getJobStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PrintQueueItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(printQueueItemRepository, times(2)).saveAll(captor.capture());
        List<PrintQueueItem> created = captor.getAllValues().get(1);
        assertEquals(1, created.size());
        PrintQueueItem job = created.getFirst();
        assertEquals(900L, job.getOrderId());
        assertNull(job.getAssetId());
        assertEquals(QueuePriority.URGENT, job.getPriority());
        assertEquals(PrintJobStatus.QUEUED, job.getJobStatus());
        assertEquals("Ana Souza", job.getCustomerName());
        assertEquals("Pedido PED-900", job.getTitle());
        assertEquals(1, job.getPosition());
    }

    @Test
    void updateJobReassignsPrinterAndPriorityButRejectsRunningJobs() {
        PrintQueueItem job = new PrintQueueItem();
        job.setId(300L);
        job.setGuid("job-300");
        job.setCompanyId(10L);
        job.setAssetId(null);
        job.setPosition(1);
        job.setJobStatus(PrintJobStatus.QUEUED);
        job.setPriority(QueuePriority.NORMAL);
        job.setCopies(1);
        job.setCompletedCopies(0);

        when(printQueueItemRepository.findByGuidAndStatus("job-300", StatusCd.ACTIVE)).thenReturn(Optional.of(job));

        assertThrows(BadRequestException.class, () -> service.transitionJob("job-300",
                new PrintJobTransitionRequest(PrintJobAction.START, null, null)));
        assertThrows(BadRequestException.class, () -> service.updateJob("job-300",
                new UpdatePrintQueueItemRequest("prt-100", true, null, null)));

        when(assetRepository.findByGuidAndStatus("prt-100", StatusCd.ACTIVE)).thenReturn(Optional.of(printer));
        when(printQueueItemRepository.save(any(PrintQueueItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.findAllByIdIn(any())).thenReturn(List.of(printer));

        PrintQueueItemResponse updated = service.updateJob("job-300",
                new UpdatePrintQueueItemRequest("prt-100", null, QueuePriority.URGENT, null));

        assertEquals(100L, job.getAssetId());
        assertEquals("prt-100", updated.printerGuid());
        assertEquals(QueuePriority.URGENT, updated.priority());

        job.setJobStatus(PrintJobStatus.PRINTING);
        Asset other = new Asset();
        other.setId(101L);
        other.setGuid("prt-101");
        other.setCompanyId(10L);
        when(assetRepository.findByGuidAndStatus("prt-101", StatusCd.ACTIVE)).thenReturn(Optional.of(other));
        assertThrows(ConflictException.class, () -> service.updateJob("job-300",
                new UpdatePrintQueueItemRequest("prt-101", null, null, null)));
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

    @Test
    void listQueueCancelsQueuedJobsWhenOrderLeftProductionButKeepsRunningJobs() {
        Order transitOrder = order(910L, "PED-910");
        Order productionOrder = order(911L, "PED-911");

        PrintQueueItem queued = job(1L, "job-queued", 910L, PrintJobStatus.QUEUED, 1);
        PrintQueueItem printing = job(2L, "job-printing", 911L, PrintJobStatus.PRINTING, 2);

        when(orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(transitOrder, productionOrder));
        when(makerOrderSupport.latestStatuses(any())).thenReturn(Map.of(
                910L, OrderStatusCd.IN_TRANSIT,
                911L, OrderStatusCd.IN_PRODUCTION
        ));
        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(queued, printing));

        service.listQueue(null, null, null, null, PageRequest.of(0, 20));

        assertEquals(PrintJobStatus.CANCELLED, queued.getJobStatus());
        assertEquals(PrintJobStatus.PRINTING, printing.getJobStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PrintQueueItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(printQueueItemRepository).saveAll(captor.capture());
        assertEquals(List.of(queued), captor.getValue());
    }

    @Test
    void listQueueInsertsNewUrgentJobBeforeLessUrgentQueuedJob() {
        Order existingOrder = order(800L, "PED-800");
        Order newOrder = order(900L, "PED-900");
        PrintQueueItem existingJob = job(1L, "job-existing", 800L, PrintJobStatus.QUEUED, 1);
        existingJob.setPriority(QueuePriority.NORMAL);

        when(orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(existingOrder, newOrder));
        when(makerOrderSupport.latestStatuses(any())).thenReturn(Map.of(
                800L, OrderStatusCd.IN_PRODUCTION,
                900L, OrderStatusCd.IN_PRODUCTION
        ));
        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(existingJob));
        Instant urgentDue = Instant.now().plus(Duration.ofHours(12));
        Instant laterDue = Instant.now().plus(Duration.ofDays(10));
        when(makerOrderSupport.dueDates(any(), eq(company)))
                .thenReturn(Map.of(900L, urgentDue, 800L, laterDue));
        when(makerOrderSupport.buyerNames(any())).thenReturn(Map.of(900L, "Bruno Lima"));
        when(makerOrderSupport.resolveFiles(any())).thenReturn(Map.of());
        when(printQueueItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.listQueue(null, null, null, null, PageRequest.of(0, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PrintQueueItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(printQueueItemRepository).saveAll(captor.capture());
        List<PrintQueueItem> ordered = captor.getValue();
        assertEquals(2, ordered.size());
        assertEquals(900L, ordered.get(0).getOrderId());
        assertEquals(QueuePriority.URGENT, ordered.get(0).getPriority());
        assertEquals(1, ordered.get(0).getPosition());
        assertEquals(800L, ordered.get(1).getOrderId());
        assertEquals(2, ordered.get(1).getPosition());
    }

    @Test
    void listQueueFiltersBySaleOrderStatus() {
        when(orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of());

        Order prodOrder = order(900L, "PED-900");
        Order transitOrder = order(901L, "PED-901");
        PrintQueueItem prodJob = job(1L, "job-prod", 900L, PrintJobStatus.QUEUED, 1);
        PrintQueueItem transitJob = job(2L, "job-transit", 901L, PrintJobStatus.QUEUED, 2);

        when(printQueueItemRepository.findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(prodJob, transitJob));
        when(orderRepository.findAllById(any())).thenReturn(List.of(prodOrder, transitOrder));
        when(makerOrderSupport.latestStatuses(any())).thenReturn(Map.of(
                900L, OrderStatusCd.IN_PRODUCTION,
                901L, OrderStatusCd.IN_TRANSIT
        ));
        when(makerOrderSupport.dueDates(any(), any())).thenReturn(Map.of());

        var page = service.listQueue(null, null, null, OrderStatusCd.IN_PRODUCTION, PageRequest.of(0, 20));

        assertEquals(1, page.content().size());
        assertEquals("job-prod", page.content().getFirst().guid());
        assertEquals(OrderStatusCd.IN_PRODUCTION, page.content().getFirst().orderStatus());
    }

    private static Order order(long id, String code) {
        Order order = new Order();
        order.setId(id);
        order.setGuid("ord-" + id);
        order.setOrderCode(code);
        order.setVendorId(10L);
        order.setCreateDt(Instant.now().minus(Duration.ofDays(2)));
        return order;
    }

    private static PrintQueueItem job(long id, String guid, Long orderId, PrintJobStatus status, int position) {
        PrintQueueItem job = new PrintQueueItem();
        job.setId(id);
        job.setGuid(guid);
        job.setCompanyId(10L);
        job.setOrderId(orderId);
        job.setPosition(position);
        job.setJobStatus(status);
        job.setPriority(QueuePriority.NORMAL);
        job.setCopies(1);
        job.setCompletedCopies(0);
        job.setStatus(StatusCd.ACTIVE);
        return job;
    }
}
