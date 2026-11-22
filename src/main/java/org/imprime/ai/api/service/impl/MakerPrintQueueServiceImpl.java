package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreatePrintQueueItemRequest;
import org.imprime.ai.api.http.request.maker.PrintJobTransitionRequest;
import org.imprime.ai.api.http.request.maker.ReorderPrintQueueRequest;
import org.imprime.ai.api.http.request.maker.UpdatePrintQueueItemRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerArchiveFile;
import org.imprime.ai.api.model.MakerProject;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.ProjectStatus;
import org.imprime.ai.api.model.enums.QueuePriority;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.StockAdjustmentReason;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerArchiveFileRepository;
import org.imprime.ai.api.repo.db.MakerProjectRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerOrderSupport;
import org.imprime.ai.api.service.MakerPrintQueueService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MakerPrintQueueServiceImpl implements MakerPrintQueueService {
    private static final Set<PrintJobStatus> ACTIVE_QUEUE_STATUSES = EnumSet.of(
            PrintJobStatus.QUEUED, PrintJobStatus.PRINTING, PrintJobStatus.PAUSED
    );
    private static final Set<PrintJobStatus> BUSY_PRINTER_JOB_STATUSES = EnumSet.of(
            PrintJobStatus.PRINTING, PrintJobStatus.PAUSED
    );

    private final MakerCompanyResolver makerCompanyResolver;
    private final PrintQueueItemRepository printQueueItemRepository;
    private final AssetRepository assetRepository;
    private final MakerArchiveFileRepository archiveFileRepository;
    private final FilamentRepository filamentRepository;
    private final OrderRepository orderRepository;
    private final MakerProjectRepository projectRepository;
    private final CompanyRepository companyRepository;
    private final MakerFilamentServiceImpl makerFilamentService;
    private final JsonCodec jsonCodec;
    private final MakerOrderSupport makerOrderSupport;

    private static final Set<OrderStatusCd> LEFT_PRODUCTION_STATUSES = EnumSet.of(
            OrderStatusCd.IN_TRANSIT, OrderStatusCd.COMPLETED,
            OrderStatusCd.CANCELLED, OrderStatusCd.REFUNDED, OrderStatusCd.ERROR
    );

    @Override
    @Transactional
    public PageResponse<PrintQueueItemResponse> listQueue(
            PrintJobStatus status,
            String printerGuid,
            Boolean activeOnly,
            OrderStatusCd orderStatus,
            Pageable pageable) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        syncOrdersInProduction(company);
        List<PrintQueueItem> allJobs = printQueueItemRepository
                .findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(company.getId(), StatusCd.ACTIVE);

        if (allJobs.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        List<PrintQueueItem> filtered = allJobs.stream()
                .filter(job -> status == null || status.equals(job.getJobStatus()))
                .filter(job -> !Boolean.TRUE.equals(activeOnly) || ACTIVE_QUEUE_STATUSES.contains(job.getJobStatus()))
                .toList();

        List<PrintQueueItemResponse> mapped = mapResponsesBatch(filtered);
        if (orderStatus != null) {
            mapped = mapped.stream()
                    .filter(item -> orderStatus.equals(item.orderStatus()))
                    .toList();
        }
        if (printerGuid != null && !printerGuid.isBlank()) {
            String targetPrinterGuid = printerGuid.trim();
            mapped = mapped.stream()
                    .filter(item -> targetPrinterGuid.equals(item.printerGuid()))
                    .toList();
        }

        return PageUtils.paginate(mapped, pageable);
    }

    @Override
    @Transactional
    public PrintQueueItemResponse enqueueJob(CreatePrintQueueItemRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Asset printer = requireOwnedPrinter(request.printerGuid(), company);

        MakerArchiveFile archiveFile = null;
        if (request.fileGuid() != null && !request.fileGuid().isBlank()) {
            archiveFile = archiveFileRepository.findByGuidAndStatus(request.fileGuid().trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND));
            if (!Objects.equals(company.getId(), archiveFile.getCompanyId())) {
                throw new ForbiddenException();
            }
        }

        Filament filament = resolveOwnedFilament(request.filamentGuid(), archiveFile != null ? archiveFile.getFilamentId() : null, company);
        Order order = resolveCompanyOrder(request.orderGuid(), company);
        MakerProject project = resolveOwnedProject(request.projectGuid(), archiveFile != null ? archiveFile.getMakerProjectId() : null, company);

        return enqueueBuiltJob(
                company,
                printer,
                archiveFile,
                filament,
                order,
                project,
                request.title().trim(),
                CatalogDtoMapper.firstNonBlank(request.fileName(), archiveFile != null ? archiveFile.getFileName() : null),
                request.material() != null ? request.material() : (archiveFile != null ? archiveFile.getMaterial() : (filament != null ? filament.getMaterial() : null)),
                CatalogDtoMapper.firstNonBlank(request.colorName(), archiveFile != null ? archiveFile.getColorName() : null, filament != null ? filament.getColorName() : null),
                CatalogDtoMapper.firstNonBlank(request.colorHex(), archiveFile != null ? archiveFile.getColorHex() : null, filament != null ? filament.getColorHex() : null),
                request.estimatedWeightGrams() != null ? request.estimatedWeightGrams() : (archiveFile != null ? archiveFile.getEstimatedWeightGrams() : null),
                request.estimatedTimeMinutes() != null ? request.estimatedTimeMinutes() : (archiveFile != null ? archiveFile.getEstimatedTimeMinutes() : null),
                request.priority() != null ? request.priority() : QueuePriority.NORMAL,
                request.copies() != null ? request.copies() : 1,
                request.customerName(),
                request.scheduledStartAt()
        );
    }

    @Transactional
    public PrintQueueItemResponse enqueueBuiltJob(
            Company company,
            Asset printer,
            MakerArchiveFile archiveFile,
            Filament filament,
            Order order,
            MakerProject project,
            String title,
            String fileName,
            org.imprime.ai.api.model.enums.MaterialType material,
            String colorName,
            String colorHex,
            BigDecimal estimatedWeightGrams,
            Integer estimatedTimeMinutes,
            QueuePriority priority,
            Integer copies,
            String customerName,
            Instant scheduledStartAt) {
        int nextPos = nextQueuePosition(company.getId());

        PrintQueueItem job = new PrintQueueItem();
        job.setCompanyId(company.getId());
        job.setAssetId(printer.getId());
        job.setMakerArchiveFileId(archiveFile != null ? archiveFile.getId() : null);
        job.setFilamentId(filament != null ? filament.getId() : null);
        job.setOrderId(order != null ? order.getId() : null);
        job.setMakerProjectId(project != null ? project.getId() : null);
        job.setPosition(nextPos);
        job.setTitle(title);
        job.setFileName(fileName);
        job.setMaterial(material);
        job.setColorName(colorName);
        job.setColorHex(colorHex);
        job.setEstimatedWeightGrams(estimatedWeightGrams);
        job.setEstimatedTimeMinutes(estimatedTimeMinutes);
        job.setProgressPercent(0);
        job.setJobStatus(PrintJobStatus.QUEUED);
        job.setPriority(priority != null ? priority : QueuePriority.NORMAL);
        job.setCopies(copies != null && copies > 0 ? copies : 1);
        job.setCompletedCopies(0);
        job.setCustomerName(customerName);
        job.setScheduledStartAt(scheduledStartAt);

        PrintQueueItem saved = printQueueItemRepository.save(job);
        return toSingleResponse(saved, printer, archiveFile, filament, order, project,
                order != null ? makerOrderSupport.dueDates(List.of(order), company).get(order.getId()) : null,
                order != null ? makerOrderSupport.latestStatuses(List.of(order.getId())).get(order.getId()) : null);
    }

    @Override
    @Transactional
    public PrintQueueItemResponse updateJob(String jobGuid, UpdatePrintQueueItemRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        PrintQueueItem job = requireOwnedJob(jobGuid, company);

        boolean wantsUnassign = Boolean.TRUE.equals(request.unassignPrinter());
        boolean wantsPrinter = request.printerGuid() != null && !request.printerGuid().isBlank();
        if (wantsPrinter && wantsUnassign) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        if (wantsPrinter || wantsUnassign) {
            Long newAssetId = wantsPrinter ? requireOwnedPrinter(request.printerGuid(), company).getId() : null;
            if (!Objects.equals(newAssetId, job.getAssetId())) {
                if (BUSY_PRINTER_JOB_STATUSES.contains(job.getJobStatus())) {
                    throw new ConflictException(MessageCd.PRINTER_BUSY);
                }
                job.setAssetId(newAssetId);
            }
        }

        if (request.priority() != null) {
            job.setPriority(request.priority());
        }

        if (request.copies() != null) {
            int completed = job.getCompletedCopies() != null ? job.getCompletedCopies() : 0;
            if (request.copies() < 1 || request.copies() < completed) {
                throw new BadRequestException(MessageCd.VALIDATION_ERROR);
            }
            job.setCopies(request.copies());
        }

        PrintQueueItem saved = printQueueItemRepository.save(job);
        return mapResponsesBatch(List.of(saved)).getFirst();
    }

    /**
     * Garante que toda venda do estúdio com status atual "Em produção" tenha um job na fila,
     * cancela jobs ainda não iniciados de vendas que saíram de produção (Em trânsito, Concluído,
     * Cancelado, Reembolsado ou com erro) e insere os novos jobs respeitando prioridade e prazo
     * em relação aos jobs já enfileirados, sem reordenar manualmente os jobs existentes.
     */
    private void syncOrdersInProduction(Company company) {
        List<Order> orders = orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(company.getId(), StatusCd.ACTIVE);
        if (orders == null || orders.isEmpty()) {
            return;
        }
        Map<Long, OrderStatusCd> statuses = makerOrderSupport.latestStatuses(
                orders.stream().map(Order::getId).filter(Objects::nonNull).toList());
        if (statuses == null || statuses.isEmpty()) {
            return;
        }

        Map<Long, Order> ordersById = orders.stream()
                .filter(order -> order.getId() != null)
                .collect(Collectors.toMap(Order::getId, order -> order, (a, b) -> a));

        List<Order> inProduction = orders.stream()
                .filter(order -> OrderStatusCd.IN_PRODUCTION.equals(statuses.get(order.getId())))
                .toList();

        List<PrintQueueItem> activeJobs = printQueueItemRepository
                .findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(company.getId(), StatusCd.ACTIVE);
        if (activeJobs == null) {
            activeJobs = List.of();
        }

        // Decisão de produto: venda que saiu de produção cancela seus jobs ainda QUEUED.
        List<PrintQueueItem> toCancel = activeJobs.stream()
                .filter(job -> job.getOrderId() != null)
                .filter(job -> PrintJobStatus.QUEUED.equals(job.getJobStatus()))
                .filter(job -> {
                    OrderStatusCd orderStatus = statuses.get(job.getOrderId());
                    return orderStatus != null && LEFT_PRODUCTION_STATUSES.contains(orderStatus);
                })
                .toList();
        Set<Long> cancelledJobIds = new HashSet<>();
        if (!toCancel.isEmpty()) {
            toCancel.forEach(job -> {
                job.setJobStatus(PrintJobStatus.CANCELLED);
                cancelledJobIds.add(job.getId());
            });
            printQueueItemRepository.saveAll(toCancel);
        }

        // Um job só "ocupa" a venda enquanto está ativo; jobs terminais permitem recriar o job.
        Set<Long> alreadyQueued = activeJobs.stream()
                .filter(job -> !cancelledJobIds.contains(job.getId()))
                .filter(job -> ACTIVE_QUEUE_STATUSES.contains(job.getJobStatus()))
                .map(PrintQueueItem::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Order> missing = inProduction.stream()
                .filter(order -> !alreadyQueued.contains(order.getId()))
                .toList();
        if (missing.isEmpty()) {
            return;
        }

        Map<Long, Instant> dueDates = makerOrderSupport.dueDates(missing, company);
        Map<Long, String> buyerNames = makerOrderSupport.buyerNames(missing);
        Map<Long, List<MakerOrderSupport.ResolvedOrderFile>> filesByOrder = makerOrderSupport.resolveFiles(missing);

        List<Order> sorted = new ArrayList<>(missing);
        sorted.sort(java.util.Comparator.comparing(
                (Order order) -> dueDates.getOrDefault(order.getId(), Instant.MAX)));

        List<PrintQueueItem> created = new ArrayList<>(sorted.size());
        for (Order order : sorted) {
            List<MakerOrderSupport.ResolvedOrderFile> files = filesByOrder.getOrDefault(order.getId(), List.of());
            MakerOrderSupport.ResolvedOrderFile firstFile = files.isEmpty() ? null : files.getFirst();
            String code = order.getOrderCode() != null && !order.getOrderCode().isBlank()
                    ? order.getOrderCode()
                    : "#" + order.getId();
            String title = "Pedido " + code + (files.size() > 1 ? " — " + files.size() + " arquivos" : "");

            PrintQueueItem job = new PrintQueueItem();
            job.setCompanyId(company.getId());
            job.setAssetId(null);
            job.setOrderId(order.getId());
            job.setTitle(title.length() > 150 ? title.substring(0, 150) : title);
            // Decisão de produto: venda sem arquivo 3D continua na fila (sinalizada como "sem arquivo" na UI).
            job.setFileName(firstFile != null ? truncate(firstFile.asset().getName(), 255) : null);
            job.setMaterial(parseMaterial(firstFile != null ? firstFile.material() : null));
            job.setColorHex(firstFile != null && firstFile.colorHex() != null && firstFile.colorHex().length() <= 7
                    ? firstFile.colorHex() : null);
            job.setProgressPercent(0);
            job.setJobStatus(PrintJobStatus.QUEUED);
            job.setPriority(priorityForDueDate(dueDates.get(order.getId())));
            job.setCopies(1);
            job.setCompletedCopies(0);
            job.setCustomerName(truncate(buyerNames.get(order.getId()), 120));
            created.add(job);
        }

        List<PrintQueueItem> remaining = activeJobs.stream()
                .filter(job -> !cancelledJobIds.contains(job.getId()))
                .sorted(java.util.Comparator.comparing(
                        (PrintQueueItem job) -> job.getPosition() != null ? job.getPosition() : Integer.MAX_VALUE))
                .collect(Collectors.toCollection(ArrayList::new));

        List<Order> existingOrderJobs = remaining.stream()
                .map(job -> job.getOrderId() != null ? ordersById.get(job.getOrderId()) : null)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Instant> existingDueDates = existingOrderJobs.isEmpty()
                ? Collections.emptyMap()
                : makerOrderSupport.dueDates(existingOrderJobs, company);

        insertJobsByUrgency(remaining, created, dueDates, existingDueDates);

        int position = 1;
        for (PrintQueueItem job : remaining) {
            job.setPosition(position++);
        }
        printQueueItemRepository.saveAll(remaining);
    }

    /**
     * Insere os novos jobs na ordem já existente, antes do primeiro job QUEUED menos urgente.
     * Jobs em execução (PRINTING/PAUSED) e a ordem relativa dos jobs existentes são preservados.
     */
    private void insertJobsByUrgency(
            List<PrintQueueItem> ordered,
            List<PrintQueueItem> created,
            Map<Long, Instant> createdDueDates,
            Map<Long, Instant> existingDueDates) {
        List<PrintQueueItem> pending = new ArrayList<>(created);
        pending.sort((a, b) -> compareUrgency(
                a, createdDueDates.get(a.getOrderId()),
                b, createdDueDates.get(b.getOrderId())));

        for (PrintQueueItem newJob : pending) {
            Instant newDue = createdDueDates.get(newJob.getOrderId());
            int insertAt = ordered.size();
            for (int i = 0; i < ordered.size(); i++) {
                PrintQueueItem existing = ordered.get(i);
                if (!PrintJobStatus.QUEUED.equals(existing.getJobStatus())) {
                    continue;
                }
                Instant existingDue = existing.getOrderId() != null
                        ? existingDueDates.get(existing.getOrderId())
                        : null;
                if (isMoreUrgent(newJob.getPriority(), newDue, existing.getPriority(), existingDue)) {
                    insertAt = i;
                    break;
                }
            }
            ordered.add(insertAt, newJob);
        }
    }

    private static int compareUrgency(PrintQueueItem a, Instant aDue, PrintQueueItem b, Instant bDue) {
        int byPriority = Integer.compare(priorityRank(b.getPriority()), priorityRank(a.getPriority()));
        if (byPriority != 0) {
            return byPriority;
        }
        if (aDue != null && bDue != null) {
            return aDue.compareTo(bDue);
        }
        if (aDue != null) {
            return -1;
        }
        return bDue != null ? 1 : 0;
    }

    private static boolean isMoreUrgent(QueuePriority a, Instant aDue, QueuePriority b, Instant bDue) {
        int rankA = priorityRank(a);
        int rankB = priorityRank(b);
        if (rankA != rankB) {
            return rankA > rankB;
        }
        if (aDue != null && bDue != null) {
            return aDue.isBefore(bDue);
        }
        return aDue != null && bDue == null;
    }

    private static int priorityRank(QueuePriority priority) {
        return switch (priority != null ? priority : QueuePriority.NORMAL) {
            case LOW -> 1;
            case NORMAL -> 2;
            case HIGH -> 3;
            case URGENT -> 4;
        };
    }

    static QueuePriority priorityForDueDate(Instant dueAt) {
        if (dueAt == null) {
            return QueuePriority.NORMAL;
        }
        long hoursLeft = java.time.Duration.between(Instant.now(), dueAt).toHours();
        if (hoursLeft <= 24) {
            return QueuePriority.URGENT;
        }
        if (hoursLeft <= 48) {
            return QueuePriority.HIGH;
        }
        return QueuePriority.NORMAL;
    }

    private static org.imprime.ai.api.model.enums.MaterialType parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (org.imprime.ai.api.model.enums.MaterialType type : org.imprime.ai.api.model.enums.MaterialType.values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    @Override
    @Transactional
    public void deleteJob(String jobGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        PrintQueueItem job = requireOwnedJob(jobGuid, company);

        if (BUSY_PRINTER_JOB_STATUSES.contains(job.getJobStatus())) {
            releasePrinterIfIdle(job.getAssetId(), job.getId());
        }

        job.setStatus(StatusCd.INACTIVE);
        printQueueItemRepository.save(job);
        reindexActivePositions(company.getId(), job.getId());
    }

    @Override
    @Transactional
    public PrintQueueItemResponse transitionJob(String jobGuid, PrintJobTransitionRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        PrintQueueItem job = requireOwnedJob(jobGuid, company);
        PrintJobStatus current = job.getJobStatus();

        Asset targetPrinter;
        if (request.printerGuid() != null && !request.printerGuid().isBlank()) {
            targetPrinter = requireOwnedPrinter(request.printerGuid(), company);
        } else if (job.getAssetId() != null) {
            targetPrinter = assetRepository.findById(job.getAssetId())
                    .orElseThrow(() -> new NotFoundException(MessageCd.PRINTER_NOT_FOUND));
        } else {
            targetPrinter = null;
        }

        boolean needsPrinter = switch (request.action()) {
            case START, PAUSE, RESUME, COMPLETE -> true;
            default -> false;
        };
        if (needsPrinter && targetPrinter == null) {
            throw new BadRequestException(MessageCd.PRINTER_NOT_FOUND);
        }

        switch (request.action()) {
            case START -> {
                if (!PrintJobStatus.QUEUED.equals(current)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                ensurePrinterAvailableForJob(targetPrinter, job.getId());
                job.setAssetId(targetPrinter.getId());
                job.setJobStatus(PrintJobStatus.PRINTING);
                targetPrinter.setPrinterStatus(PrinterStatus.PRINTING);
                assetRepository.save(targetPrinter);
            }
            case PAUSE -> {
                if (!PrintJobStatus.PRINTING.equals(current)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                job.setJobStatus(PrintJobStatus.PAUSED);
                targetPrinter.setPrinterStatus(PrinterStatus.PAUSED);
                assetRepository.save(targetPrinter);
            }
            case RESUME -> {
                if (!PrintJobStatus.PAUSED.equals(current)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                if (!Objects.equals(job.getAssetId(), targetPrinter.getId())) {
                    releasePrinterIfIdle(job.getAssetId(), job.getId());
                    ensurePrinterAvailableForJob(targetPrinter, job.getId());
                    job.setAssetId(targetPrinter.getId());
                } else if (PrinterStatus.OFFLINE.equals(targetPrinter.getPrinterStatus())
                        || PrinterStatus.MAINTENANCE.equals(targetPrinter.getPrinterStatus())) {
                    throw new ConflictException(MessageCd.PRINTER_BUSY);
                }
                job.setJobStatus(PrintJobStatus.PRINTING);
                targetPrinter.setPrinterStatus(PrinterStatus.PRINTING);
                assetRepository.save(targetPrinter);
            }
            case COMPLETE -> {
                if (!BUSY_PRINTER_JOB_STATUSES.contains(current)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                int completedCopies = (job.getCompletedCopies() != null ? job.getCompletedCopies() : 0) + 1;
                int totalCopies = job.getCopies() != null && job.getCopies() > 0 ? job.getCopies() : 1;
                job.setCompletedCopies(completedCopies);

                if (completedCopies >= totalCopies) {
                    job.setJobStatus(PrintJobStatus.COMPLETED);
                    job.setProgressPercent(100);
                } else {
                    job.setJobStatus(PrintJobStatus.QUEUED);
                    job.setProgressPercent(0);
                }

                releasePrinterAndAddHours(targetPrinter, job.getId(), job.getEstimatedTimeMinutes());

                makerFilamentService.consumeStockForPrint(
                        job.getFilamentId(),
                        job.getEstimatedWeightGrams(),
                        StockAdjustmentReason.PRINT_CONSUMPTION,
                        job.getId(),
                        "Impressão concluída: " + job.getTitle()
                );

                company.setPrintsDone((company.getPrintsDone() != null ? company.getPrintsDone() : 0) + 1);
                companyRepository.save(company);

                advanceProjectProgress(job.getMakerProjectId());
            }
            case FAIL -> {
                if (!ACTIVE_QUEUE_STATUSES.contains(current)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                boolean hadStarted = BUSY_PRINTER_JOB_STATUSES.contains(current);
                job.setJobStatus(PrintJobStatus.FAILED);
                job.setFailureReason(request.failureReason());
                if (hadStarted) {
                    releasePrinterIfIdle(job.getAssetId(), job.getId());
                    makerFilamentService.consumeStockForPrint(
                            job.getFilamentId(),
                            job.getEstimatedWeightGrams(),
                            StockAdjustmentReason.FAILED_PRINT,
                            job.getId(),
                            "Falha na impressão: " + job.getTitle()
                    );
                }
            }
            case RETRY -> {
                if (!PrintJobStatus.FAILED.equals(current) && !PrintJobStatus.CANCELLED.equals(current)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                job.setJobStatus(PrintJobStatus.QUEUED);
                job.setProgressPercent(0);
                job.setFailureReason(null);
                job.setPosition(nextQueuePosition(company.getId()));
            }
            case CANCEL -> {
                if (!ACTIVE_QUEUE_STATUSES.contains(current)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                if (BUSY_PRINTER_JOB_STATUSES.contains(current)) {
                    releasePrinterIfIdle(job.getAssetId(), job.getId());
                }
                job.setJobStatus(PrintJobStatus.CANCELLED);
            }
        }

        PrintQueueItem saved = printQueueItemRepository.save(job);
        return mapResponsesBatch(List.of(saved)).getFirst();
    }

    @Override
    @Transactional
    public List<PrintQueueItemResponse> reorderQueue(ReorderPrintQueueRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<String> requestedGuids = request.orderedJobGuids();

        Set<String> uniqueGuids = new HashSet<>(requestedGuids);
        if (uniqueGuids.size() != requestedGuids.size()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        List<PrintQueueItem> requestedJobs = printQueueItemRepository.findAllByCompanyIdAndGuidInAndStatus(
                company.getId(), requestedGuids, StatusCd.ACTIVE
        );
        Map<String, PrintQueueItem> byGuid = requestedJobs.stream()
                .collect(Collectors.toMap(PrintQueueItem::getGuid, j -> j, (a, b) -> a));

        for (String guid : requestedGuids) {
            if (!byGuid.containsKey(guid)) {
                throw new NotFoundException(MessageCd.QUEUE_ITEM_NOT_FOUND);
            }
        }

        List<PrintQueueItem> allActive = printQueueItemRepository
                .findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(company.getId(), StatusCd.ACTIVE);

        List<PrintQueueItem> ordered = new ArrayList<>(allActive.size());
        for (String guid : requestedGuids) {
            ordered.add(byGuid.get(guid));
        }
        for (PrintQueueItem item : allActive) {
            if (!uniqueGuids.contains(item.getGuid())) {
                ordered.add(item);
            }
        }

        int pos = 1;
        for (PrintQueueItem item : ordered) {
            item.setPosition(pos++);
        }
        List<PrintQueueItem> savedAll = printQueueItemRepository.saveAll(ordered);
        return mapResponsesBatch(savedAll);
    }

    private void ensurePrinterAvailableForJob(Asset printer, Long selfJobId) {
        if (PrinterStatus.OFFLINE.equals(printer.getPrinterStatus())
                || PrinterStatus.MAINTENANCE.equals(printer.getPrinterStatus())
                || PrinterStatus.ERROR.equals(printer.getPrinterStatus())
                || PrinterStatus.PRINTING.equals(printer.getPrinterStatus())
                || PrinterStatus.PAUSED.equals(printer.getPrinterStatus())) {
            throw new ConflictException(MessageCd.PRINTER_BUSY);
        }
        boolean anotherRunning = printQueueItemRepository
                .findAllByAssetIdAndJobStatusInAndStatus(printer.getId(), BUSY_PRINTER_JOB_STATUSES, StatusCd.ACTIVE)
                .stream()
                .anyMatch(other -> !Objects.equals(other.getId(), selfJobId));
        if (anotherRunning) {
            throw new ConflictException(MessageCd.PRINTER_BUSY);
        }
    }

    private void releasePrinterIfIdle(Long assetId, Long finishingJobId) {
        if (assetId == null) {
            return;
        }
        boolean hasOtherRunning = printQueueItemRepository
                .findAllByAssetIdAndJobStatusInAndStatus(assetId, BUSY_PRINTER_JOB_STATUSES, StatusCd.ACTIVE)
                .stream()
                .anyMatch(other -> !Objects.equals(other.getId(), finishingJobId));
        if (!hasOtherRunning) {
            assetRepository.findById(assetId).ifPresent(printer -> {
                if (PrinterStatus.PRINTING.equals(printer.getPrinterStatus())
                        || PrinterStatus.PAUSED.equals(printer.getPrinterStatus())) {
                    printer.setPrinterStatus(PrinterStatus.ONLINE);
                    assetRepository.save(printer);
                }
            });
        }
    }

    private void releasePrinterAndAddHours(Asset printer, Long finishingJobId, Integer estimatedTimeMinutes) {
        boolean hasOtherRunning = printQueueItemRepository
                .findAllByAssetIdAndJobStatusInAndStatus(printer.getId(), BUSY_PRINTER_JOB_STATUSES, StatusCd.ACTIVE)
                .stream()
                .anyMatch(other -> !Objects.equals(other.getId(), finishingJobId));

        if (!hasOtherRunning) {
            printer.setPrinterStatus(PrinterStatus.ONLINE);
        }

        if (estimatedTimeMinutes != null && estimatedTimeMinutes > 0) {
            MakerPrinterServiceImpl.StoredPrinterTelemetry telemetry =
                    jsonCodec.readObject(printer.getTelemetryJson(), MakerPrinterServiceImpl.StoredPrinterTelemetry.class);
            if (telemetry == null) {
                telemetry = new MakerPrinterServiceImpl.StoredPrinterTelemetry(
                        new BigDecimal("25.0"), BigDecimal.ZERO, new BigDecimal("25.0"), BigDecimal.ZERO,
                        null, 0, 0, 0, BigDecimal.ZERO
                );
            }
            BigDecimal currentHours = telemetry.totalPrintHours() != null ? telemetry.totalPrintHours() : BigDecimal.ZERO;
            BigDecimal addedHours = BigDecimal.valueOf(estimatedTimeMinutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            MakerPrinterServiceImpl.StoredPrinterTelemetry updated = new MakerPrinterServiceImpl.StoredPrinterTelemetry(
                    telemetry.nozzleTempC(),
                    telemetry.nozzleTargetC(),
                    telemetry.bedTempC(),
                    telemetry.bedTargetC(),
                    telemetry.chamberTempC(),
                    telemetry.fanSpeedPercent(),
                    telemetry.currentLayer(),
                    telemetry.totalLayers(),
                    currentHours.add(addedHours)
            );
            printer.setTelemetryJson(jsonCodec.toJson(updated));
        }

        assetRepository.save(printer);
    }

    private void advanceProjectProgress(Long makerProjectId) {
        if (makerProjectId == null) {
            return;
        }
        projectRepository.findById(makerProjectId)
                .filter(p -> StatusCd.ACTIVE.equals(p.getStatus()))
                .ifPresent(project -> {
                    int totalPlates = project.getTotalPlates() != null ? project.getTotalPlates() : 1;
                    int nextPlates = Math.min(totalPlates,
                            (project.getCompletedPlates() != null ? project.getCompletedPlates() : 0) + 1);
                    project.setCompletedPlates(nextPlates);
                    if (nextPlates >= totalPlates) {
                        project.setProjectStatus(ProjectStatus.COMPLETED);
                    } else if (ProjectStatus.PLANNING.equals(project.getProjectStatus())) {
                        project.setProjectStatus(ProjectStatus.IN_PROGRESS);
                    }
                    projectRepository.save(project);
                });
    }

    private int nextQueuePosition(Long companyId) {
        List<PrintQueueItem> current = printQueueItemRepository
                .findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(companyId, StatusCd.ACTIVE);
        return current.stream()
                .map(PrintQueueItem::getPosition)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private void reindexActivePositions(Long companyId, Long excludedJobId) {
        List<PrintQueueItem> remaining = printQueueItemRepository
                .findAllByCompanyIdAndStatusOrderByPositionAscCreateDtAsc(companyId, StatusCd.ACTIVE)
                .stream()
                .filter(item -> !Objects.equals(item.getId(), excludedJobId))
                .toList();
        int pos = 1;
        for (PrintQueueItem item : remaining) {
            item.setPosition(pos++);
        }
        printQueueItemRepository.saveAll(remaining);
    }

    private PrintQueueItem requireOwnedJob(String jobGuid, Company company) {
        PrintQueueItem job = printQueueItemRepository.findByGuidAndStatus(jobGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.QUEUE_ITEM_NOT_FOUND));
        if (!Objects.equals(company.getId(), job.getCompanyId())) {
            throw new ForbiddenException();
        }
        return job;
    }

    private Asset requireOwnedPrinter(String printerGuid, Company company) {
        Asset printer = assetRepository.findByGuidAndStatus(printerGuid.trim(), StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PRINTER_NOT_FOUND));
        if (!Objects.equals(company.getId(), printer.getCompanyId())) {
            throw new ForbiddenException();
        }
        return printer;
    }

    private Filament resolveOwnedFilament(String requestedGuid, Long fallbackFilamentId, Company company) {
        if (requestedGuid != null && !requestedGuid.isBlank()) {
            Filament filament = filamentRepository.findByGuidAndStatus(requestedGuid.trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new NotFoundException(MessageCd.FILAMENT_NOT_FOUND));
            if (!Objects.equals(company.getId(), filament.getCompanyId())) {
                throw new ForbiddenException();
            }
            return filament;
        }
        if (fallbackFilamentId != null) {
            return filamentRepository.findById(fallbackFilamentId)
                    .filter(f -> StatusCd.ACTIVE.equals(f.getStatus()) && Objects.equals(company.getId(), f.getCompanyId()))
                    .orElse(null);
        }
        return null;
    }

    private Order resolveCompanyOrder(String orderGuid, Company company) {
        if (orderGuid == null || orderGuid.isBlank()) {
            return null;
        }
        Order order = orderRepository.findOrderByGuid(orderGuid.trim())
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        if (!Objects.equals(company.getId(), order.getVendorId())) {
            throw new ForbiddenException();
        }
        return order;
    }

    private MakerProject resolveOwnedProject(String requestedGuid, Long fallbackProjectId, Company company) {
        if (requestedGuid != null && !requestedGuid.isBlank()) {
            MakerProject project = projectRepository.findByGuidAndStatus(requestedGuid.trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new NotFoundException(MessageCd.PROJECT_NOT_FOUND));
            if (!Objects.equals(company.getId(), project.getCompanyId())) {
                throw new ForbiddenException();
            }
            return project;
        }
        if (fallbackProjectId != null) {
            return projectRepository.findById(fallbackProjectId)
                    .filter(p -> StatusCd.ACTIVE.equals(p.getStatus()) && Objects.equals(company.getId(), p.getCompanyId()))
                    .orElse(null);
        }
        return null;
    }

    private List<PrintQueueItemResponse> mapResponsesBatch(List<PrintQueueItem> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }

        Set<Long> assetIds = jobs.stream().map(PrintQueueItem::getAssetId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Asset> assetsById = assetIds.isEmpty()
                ? Collections.emptyMap()
                : assetRepository.findAllByIdIn(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a, (a, b) -> a));

        Set<Long> fileIds = jobs.stream().map(PrintQueueItem::getMakerArchiveFileId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MakerArchiveFile> filesById = fileIds.isEmpty()
                ? Collections.emptyMap()
                : archiveFileRepository.findAllByIdIn(fileIds).stream()
                .collect(Collectors.toMap(MakerArchiveFile::getId, f -> f, (a, b) -> a));

        Set<Long> filamentIds = jobs.stream().map(PrintQueueItem::getFilamentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Filament> filamentsById = filamentIds.isEmpty()
                ? Collections.emptyMap()
                : filamentRepository.findAllByIdIn(filamentIds).stream()
                .collect(Collectors.toMap(Filament::getId, f -> f, (a, b) -> a));

        Set<Long> orderIds = jobs.stream().map(PrintQueueItem::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Order> ordersById = orderIds.isEmpty()
                ? Collections.emptyMap()
                : orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));

        Set<Long> projectIds = jobs.stream().map(PrintQueueItem::getMakerProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MakerProject> projectsById = projectIds.isEmpty()
                ? Collections.emptyMap()
                : projectRepository.findAllByIdIn(projectIds).stream()
                .collect(Collectors.toMap(MakerProject::getId, p -> p, (a, b) -> a));

        Map<Long, Instant> dueDatesByOrder = ordersById.isEmpty()
                ? Collections.emptyMap()
                : makerOrderSupport.dueDates(ordersById.values(), companyRepository.findById(jobs.getFirst().getCompanyId()).orElse(null));
        Map<Long, OrderStatusCd> statusesByOrder = ordersById.isEmpty()
                ? Collections.emptyMap()
                : makerOrderSupport.latestStatuses(ordersById.keySet());

        return jobs.stream()
                .map(job -> toSingleResponse(
                        job,
                        job.getAssetId() != null ? assetsById.get(job.getAssetId()) : null,
                        job.getMakerArchiveFileId() != null ? filesById.get(job.getMakerArchiveFileId()) : null,
                        job.getFilamentId() != null ? filamentsById.get(job.getFilamentId()) : null,
                        job.getOrderId() != null ? ordersById.get(job.getOrderId()) : null,
                        job.getMakerProjectId() != null ? projectsById.get(job.getMakerProjectId()) : null,
                        job.getOrderId() != null ? dueDatesByOrder.get(job.getOrderId()) : null,
                        job.getOrderId() != null ? statusesByOrder.get(job.getOrderId()) : null
                ))
                .toList();
    }

    private PrintQueueItemResponse toSingleResponse(
            PrintQueueItem job,
            Asset printer,
            MakerArchiveFile archiveFile,
            Filament filament,
            Order order,
            MakerProject project,
            Instant orderDueAt,
            OrderStatusCd orderStatus) {
        return new PrintQueueItemResponse(
                job.getGuid(),
                job.getPosition() != null ? job.getPosition() : 0,
                job.getTitle(),
                archiveFile != null ? archiveFile.getGuid() : null,
                job.getFileName(),
                archiveFile != null ? archiveFile.getThumbnailUrl() : null,
                printer != null ? printer.getGuid() : null,
                printer != null ? printer.getName() : null,
                filament != null ? filament.getGuid() : null,
                job.getMaterial(),
                job.getColorName(),
                job.getColorHex(),
                job.getEstimatedWeightGrams(),
                job.getEstimatedTimeMinutes() != null ? job.getEstimatedTimeMinutes() : 0,
                job.getProgressPercent() != null ? job.getProgressPercent() : 0,
                job.getJobStatus(),
                job.getPriority(),
                job.getCopies() != null ? job.getCopies() : 1,
                job.getCompletedCopies() != null ? job.getCompletedCopies() : 0,
                order != null ? order.getGuid() : null,
                order != null ? order.getOrderCode() : null,
                project != null ? project.getGuid() : null,
                job.getCustomerName(),
                job.getFailureReason(),
                job.getScheduledStartAt(),
                job.getCreateDt(),
                orderDueAt,
                orderStatus,
                order != null ? order.getCreateDt() : null
        );
    }
}
