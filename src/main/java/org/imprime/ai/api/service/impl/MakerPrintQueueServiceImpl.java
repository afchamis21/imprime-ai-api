package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreatePrintQueueItemRequest;
import org.imprime.ai.api.http.request.maker.PrintJobTransitionRequest;
import org.imprime.ai.api.http.request.maker.ReorderPrintQueueRequest;
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

    @Override
    public PageResponse<PrintQueueItemResponse> listQueue(
            PrintJobStatus status,
            String printerGuid,
            Boolean activeOnly,
            Pageable pageable) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
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
        return toSingleResponse(saved, printer, archiveFile, filament, order, project);
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

        Asset targetPrinter = (request.printerGuid() != null && !request.printerGuid().isBlank())
                ? requireOwnedPrinter(request.printerGuid(), company)
                : assetRepository.findById(job.getAssetId()).orElseThrow(() -> new NotFoundException(MessageCd.PRINTER_NOT_FOUND));

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

        return jobs.stream()
                .map(job -> toSingleResponse(
                        job,
                        job.getAssetId() != null ? assetsById.get(job.getAssetId()) : null,
                        job.getMakerArchiveFileId() != null ? filesById.get(job.getMakerArchiveFileId()) : null,
                        job.getFilamentId() != null ? filamentsById.get(job.getFilamentId()) : null,
                        job.getOrderId() != null ? ordersById.get(job.getOrderId()) : null,
                        job.getMakerProjectId() != null ? projectsById.get(job.getMakerProjectId()) : null
                ))
                .toList();
    }

    private PrintQueueItemResponse toSingleResponse(
            PrintQueueItem job,
            Asset printer,
            MakerArchiveFile archiveFile,
            Filament filament,
            Order order,
            MakerProject project) {
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
                job.getCreateDt()
        );
    }
}
