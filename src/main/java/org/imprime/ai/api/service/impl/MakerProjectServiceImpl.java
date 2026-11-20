package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreateBomItemRequest;
import org.imprime.ai.api.http.request.maker.CreateMakerProjectRequest;
import org.imprime.ai.api.http.request.maker.QueueNextPlateRequest;
import org.imprime.ai.api.http.request.maker.UpdateBomItemRequest;
import org.imprime.ai.api.http.request.maker.UpdateMakerProjectRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.BomItemResponse;
import org.imprime.ai.api.http.response.maker.MakerProjectResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.MakerArchiveFile;
import org.imprime.ai.api.model.MakerProject;
import org.imprime.ai.api.model.MakerProjectBomItem;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.ProjectStatus;
import org.imprime.ai.api.model.enums.QueuePriority;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.MakerArchiveFileRepository;
import org.imprime.ai.api.repo.db.MakerProjectBomItemRepository;
import org.imprime.ai.api.repo.db.MakerProjectRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerProjectService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MakerProjectServiceImpl implements MakerProjectService {
    private final MakerCompanyResolver makerCompanyResolver;
    private final MakerProjectRepository projectRepository;
    private final MakerProjectBomItemRepository bomItemRepository;
    private final MakerArchiveFileRepository archiveFileRepository;
    private final OrderRepository orderRepository;
    private final AssetRepository assetRepository;
    private final FilamentRepository filamentRepository;
    private final MakerPrintQueueServiceImpl printQueueService;

    @Override
    public PageResponse<MakerProjectResponse> listProjects(ProjectStatus status, String query, Pageable pageable) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<MakerProject> allProjects = projectRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE);
        if (allProjects.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        String normalizedQuery = query != null && !query.isBlank() ? query.trim().toLowerCase(Locale.ROOT) : null;
        List<MakerProject> filtered = allProjects.stream()
                .filter(p -> status == null || status.equals(p.getProjectStatus()))
                .filter(p -> normalizedQuery == null
                        || containsIgnoreCase(p.getName(), normalizedQuery)
                        || containsIgnoreCase(p.getClientName(), normalizedQuery)
                        || containsIgnoreCase(p.getDescription(), normalizedQuery))
                .sorted(buildComparator(pageable))
                .toList();

        if (filtered.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        List<Long> projectIds = filtered.stream().map(MakerProject::getId).toList();
        Map<Long, List<MakerProjectBomItem>> bomByProjectId = bomItemRepository
                .findAllByMakerProjectIdInAndStatusOrderByIdAsc(projectIds, StatusCd.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(MakerProjectBomItem::getMakerProjectId));

        Map<Long, List<MakerArchiveFile>> filesByProjectId = archiveFileRepository
                .findAllByMakerProjectIdInAndStatus(projectIds, StatusCd.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(MakerArchiveFile::getMakerProjectId));

        Set<Long> orderIds = filtered.stream().map(MakerProject::getOrderId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Order> ordersById = orderIds.isEmpty()
                ? Collections.emptyMap()
                : orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, o -> o, (a, b) -> a));

        List<MakerProjectResponse> responses = filtered.stream()
                .map(project -> toResponse(
                        project,
                        project.getOrderId() != null ? ordersById.get(project.getOrderId()) : null,
                        filesByProjectId.getOrDefault(project.getId(), List.of()),
                        bomByProjectId.getOrDefault(project.getId(), List.of())
                ))
                .toList();

        return PageUtils.paginate(responses, pageable);
    }

    @Override
    public MakerProjectResponse getProject(String projectGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerProject project = requireOwnedProject(projectGuid, company);
        return assembleProjectResponse(project);
    }

    @Override
    @Transactional
    public MakerProjectResponse createProject(CreateMakerProjectRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();

        Order order = null;
        if (request.orderGuid() != null && !request.orderGuid().isBlank()) {
            order = orderRepository.findOrderByGuid(request.orderGuid().trim())
                    .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
            if (!Objects.equals(company.getId(), order.getVendorId())) {
                throw new ForbiddenException();
            }
        }

        MakerProject project = new MakerProject();
        project.setCompanyId(company.getId());
        project.setOrderId(order != null ? order.getId() : null);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setProjectStatus(ProjectStatus.PLANNING);
        project.setClientName(request.clientName());
        project.setDueDate(request.dueDate());
        project.setTotalPlates(request.totalPlates());
        project.setCompletedPlates(0);
        project.setTotalParts(request.totalParts());
        project.setCompletedParts(0);
        project.setEstimatedWeightGrams(request.estimatedWeightGrams());
        project.setEstimatedHours(request.estimatedHours());
        project.setQuotedPrice(request.quotedPrice());
        project.setEstimatedCost(request.estimatedCost());
        project.setPrimaryMaterial(request.primaryMaterial());
        project.setColorHex(request.colorHex());
        project.setNotes(request.notes());

        MakerProject saved = projectRepository.save(project);
        return toResponse(saved, order, List.of(), List.of());
    }

    @Override
    @Transactional
    public MakerProjectResponse updateProject(String projectGuid, UpdateMakerProjectRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerProject project = requireOwnedProject(projectGuid, company);

        int effectiveCompletedPlates = request.completedPlates() != null
                ? request.completedPlates()
                : (project.getCompletedPlates() != null ? project.getCompletedPlates() : 0);
        int effectiveCompletedParts = request.completedParts() != null
                ? request.completedParts()
                : (project.getCompletedParts() != null ? project.getCompletedParts() : 0);

        if (effectiveCompletedPlates > project.getTotalPlates() || effectiveCompletedParts > project.getTotalParts()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name().trim());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        if (request.dueDate() != null) {
            project.setDueDate(request.dueDate());
        }
        if (request.completedPlates() != null) {
            project.setCompletedPlates(request.completedPlates());
        }
        if (request.completedParts() != null) {
            project.setCompletedParts(request.completedParts());
        }
        if (request.notes() != null) {
            project.setNotes(request.notes());
        }
        if (request.status() != null) {
            project.setProjectStatus(request.status());
        } else if (effectiveCompletedPlates >= project.getTotalPlates()) {
            project.setProjectStatus(ProjectStatus.COMPLETED);
        }

        MakerProject saved = projectRepository.save(project);
        return assembleProjectResponse(saved);
    }

    @Override
    @Transactional
    public void deleteProject(String projectGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerProject project = requireOwnedProject(projectGuid, company);
        project.setStatus(StatusCd.INACTIVE);
        projectRepository.save(project);
    }

    @Override
    @Transactional
    public BomItemResponse addBomItem(String projectGuid, CreateBomItemRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerProject project = requireOwnedProject(projectGuid, company);

        MakerProjectBomItem item = new MakerProjectBomItem();
        item.setMakerProjectId(project.getId());
        item.setName(request.name().trim());
        item.setQuantity(request.quantity());
        item.setUnit(CatalogDtoMapper.firstNonBlank(request.unit(), "un"));
        item.setUnitCost(request.unitCost());
        item.setAcquired(Boolean.TRUE.equals(request.acquired()));

        MakerProjectBomItem saved = bomItemRepository.save(item);
        return toBomResponse(saved);
    }

    @Override
    @Transactional
    public BomItemResponse updateBomItem(String projectGuid, String bomItemGuid, UpdateBomItemRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerProject project = requireOwnedProject(projectGuid, company);
        MakerProjectBomItem item = requireProjectBomItem(bomItemGuid, project);

        if (request.name() != null && !request.name().isBlank()) {
            item.setName(request.name().trim());
        }
        if (request.quantity() != null) {
            item.setQuantity(request.quantity());
        }
        if (request.unit() != null) {
            item.setUnit(request.unit());
        }
        if (request.unitCost() != null) {
            item.setUnitCost(request.unitCost());
        }
        if (request.acquired() != null) {
            item.setAcquired(request.acquired());
        }

        MakerProjectBomItem saved = bomItemRepository.save(item);
        return toBomResponse(saved);
    }

    @Override
    @Transactional
    public void deleteBomItem(String projectGuid, String bomItemGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerProject project = requireOwnedProject(projectGuid, company);
        MakerProjectBomItem item = requireProjectBomItem(bomItemGuid, project);
        item.setStatus(StatusCd.INACTIVE);
        bomItemRepository.save(item);
    }

    @Override
    @Transactional
    public PrintQueueItemResponse queueNextPlate(String projectGuid, QueueNextPlateRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerProject project = requireOwnedProject(projectGuid, company);

        int completedPlates = project.getCompletedPlates() != null ? project.getCompletedPlates() : 0;
        int totalPlates = project.getTotalPlates() != null ? project.getTotalPlates() : 1;
        if (ProjectStatus.COMPLETED.equals(project.getProjectStatus())
                || ProjectStatus.CANCELLED.equals(project.getProjectStatus())
                || completedPlates >= totalPlates) {
            throw new ConflictException(MessageCd.PROJECT_ALREADY_COMPLETED);
        }

        String requestedPrinterGuid = request != null ? request.printerGuid() : null;
        Asset printer;
        if (requestedPrinterGuid != null && !requestedPrinterGuid.isBlank()) {
            printer = assetRepository.findByGuidAndStatus(requestedPrinterGuid.trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new NotFoundException(MessageCd.PRINTER_NOT_FOUND));
            if (!Objects.equals(company.getId(), printer.getCompanyId())) {
                throw new ForbiddenException();
            }
        } else {
            printer = MakerPrinterServiceImpl.selectEligiblePrinter(
                    assetRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE)
            );
        }

        int nextPlateNumber = completedPlates + 1;
        List<MakerArchiveFile> linkedFiles = archiveFileRepository
                .findAllByMakerProjectIdAndStatusOrderByCreateDtAsc(project.getId(), StatusCd.ACTIVE);
        MakerArchiveFile selectedFile = linkedFiles.isEmpty()
                ? null
                : linkedFiles.get(Math.min(nextPlateNumber - 1, linkedFiles.size() - 1));

        Filament filament = selectedFile != null && selectedFile.getFilamentId() != null
                ? filamentRepository.findById(selectedFile.getFilamentId())
                .filter(f -> StatusCd.ACTIVE.equals(f.getStatus()))
                .orElse(null)
                : null;

        Order order = project.getOrderId() != null
                ? orderRepository.findById(project.getOrderId()).orElse(null)
                : null;

        BigDecimal perPlateWeight = selectedFile != null
                ? selectedFile.getEstimatedWeightGrams()
                : dividePerPlate(project.getEstimatedWeightGrams(), totalPlates);

        Integer perPlateMinutes = selectedFile != null
                ? selectedFile.getEstimatedTimeMinutes()
                : (project.getEstimatedHours() != null
                ? project.getEstimatedHours().multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(totalPlates), 0, RoundingMode.HALF_UP)
                .intValue()
                : null);

        QueuePriority priority = request != null && request.priority() != null ? request.priority() : QueuePriority.HIGH;
        String title = project.getName() + " — Mesa " + nextPlateNumber + "/" + totalPlates;

        PrintQueueItemResponse queueItem = printQueueService.enqueueBuiltJob(
                company,
                printer,
                selectedFile,
                filament,
                order,
                project,
                title,
                selectedFile != null ? selectedFile.getFileName() : null,
                selectedFile != null ? selectedFile.getMaterial() : project.getPrimaryMaterial(),
                selectedFile != null ? selectedFile.getColorName() : null,
                selectedFile != null ? selectedFile.getColorHex() : project.getColorHex(),
                perPlateWeight,
                perPlateMinutes,
                priority,
                1,
                project.getClientName(),
                null
        );

        if (ProjectStatus.PLANNING.equals(project.getProjectStatus())) {
            project.setProjectStatus(ProjectStatus.IN_PROGRESS);
            projectRepository.save(project);
        }

        return queueItem;
    }

    private MakerProject requireOwnedProject(String projectGuid, Company company) {
        MakerProject project = projectRepository.findByGuidAndStatus(projectGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PROJECT_NOT_FOUND));
        if (!Objects.equals(company.getId(), project.getCompanyId())) {
            throw new ForbiddenException();
        }
        return project;
    }

    private MakerProjectBomItem requireProjectBomItem(String bomItemGuid, MakerProject project) {
        MakerProjectBomItem item = bomItemRepository.findByGuidAndStatus(bomItemGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.BOM_ITEM_NOT_FOUND));
        if (!Objects.equals(project.getId(), item.getMakerProjectId())) {
            throw new NotFoundException(MessageCd.BOM_ITEM_NOT_FOUND);
        }
        return item;
    }

    private MakerProjectResponse assembleProjectResponse(MakerProject project) {
        Order order = project.getOrderId() != null ? orderRepository.findById(project.getOrderId()).orElse(null) : null;
        List<MakerArchiveFile> files = archiveFileRepository
                .findAllByMakerProjectIdAndStatusOrderByCreateDtAsc(project.getId(), StatusCd.ACTIVE);
        List<MakerProjectBomItem> bomItems = bomItemRepository
                .findAllByMakerProjectIdAndStatusOrderByIdAsc(project.getId(), StatusCd.ACTIVE);
        return toResponse(project, order, files, bomItems);
    }

    private MakerProjectResponse toResponse(
            MakerProject project,
            Order order,
            List<MakerArchiveFile> files,
            List<MakerProjectBomItem> bomItems) {
        return new MakerProjectResponse(
                project.getGuid(),
                project.getName(),
                project.getDescription(),
                project.getProjectStatus(),
                project.getClientName(),
                order != null ? order.getGuid() : null,
                order != null ? order.getOrderCode() : null,
                project.getDueDate(),
                project.getTotalPlates() != null ? project.getTotalPlates() : 1,
                project.getCompletedPlates() != null ? project.getCompletedPlates() : 0,
                project.getTotalParts() != null ? project.getTotalParts() : 1,
                project.getCompletedParts() != null ? project.getCompletedParts() : 0,
                project.getEstimatedWeightGrams(),
                project.getEstimatedHours(),
                project.getQuotedPrice(),
                project.getEstimatedCost(),
                project.getPrimaryMaterial(),
                project.getColorHex(),
                project.getThumbnailUrl(),
                files.stream().map(MakerArchiveFile::getGuid).toList(),
                bomItems.stream().map(this::toBomResponse).toList(),
                project.getNotes(),
                project.getCreateDt()
        );
    }

    private BomItemResponse toBomResponse(MakerProjectBomItem item) {
        return new BomItemResponse(
                item.getGuid(),
                item.getName(),
                item.getQuantity(),
                item.getUnit(),
                item.getUnitCost(),
                Boolean.TRUE.equals(item.getAcquired())
        );
    }

    private BigDecimal dividePerPlate(BigDecimal total, int plates) {
        if (total == null || plates <= 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(plates), 2, RoundingMode.HALF_UP);
    }

    private Comparator<MakerProject> buildComparator(Pageable pageable) {
        Comparator<MakerProject> defaultComparator = Comparator
                .comparing(MakerProject::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MakerProject::getCreateDt, Comparator.nullsLast(Comparator.reverseOrder()));

        if (pageable == null || pageable.getSort().isUnsorted()) {
            return defaultComparator;
        }

        Comparator<MakerProject> combined = null;
        for (Sort.Order order : pageable.getSort()) {
            Comparator<MakerProject> next = switch (order.getProperty()) {
                case "dueDate" -> Comparator.comparing(
                        MakerProject::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()));
                case "name" -> Comparator.comparing(
                        MakerProject::getName, Comparator.nullsLast(String::compareToIgnoreCase));
                case "quotedPrice" -> Comparator.comparing(
                        MakerProject::getQuotedPrice, Comparator.nullsLast(BigDecimal::compareTo));
                case "createdAt", "createDt" -> Comparator.comparing(
                        MakerProject::getCreateDt, Comparator.nullsLast(Comparator.naturalOrder()));
                default -> null;
            };
            if (next != null) {
                if (order.isDescending()) {
                    next = next.reversed();
                }
                combined = combined == null ? next : combined.thenComparing(next);
            }
        }
        return combined != null ? combined : defaultComparator;
    }

    private boolean containsIgnoreCase(String value, String queryLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(queryLower);
    }
}
