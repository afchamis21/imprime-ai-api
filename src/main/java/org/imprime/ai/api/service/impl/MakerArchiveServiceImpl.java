package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.CreateArchiveFileRequest;
import org.imprime.ai.api.http.request.maker.ReprintArchiveFileRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.ArchiveFileDownloadResponse;
import org.imprime.ai.api.http.response.maker.ArchiveFileResponse;
import org.imprime.ai.api.http.response.maker.PrintQueueItemResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.MakerArchiveFile;
import org.imprime.ai.api.model.MakerProject;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.enums.FileFormat3D;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.QueuePriority;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.MakerArchiveFileRepository;
import org.imprime.ai.api.repo.db.MakerProjectRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.FileAssetService;
import org.imprime.ai.api.service.MakerArchiveService;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.ObjectStorageService;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MakerArchiveServiceImpl implements MakerArchiveService {
    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(15);
    private static final BigDecimal DEFAULT_PRINTER_KW = new BigDecimal("0.300");

    private final MakerCompanyResolver makerCompanyResolver;
    private final MakerArchiveFileRepository archiveFileRepository;
    private final FileAssetService fileAssetService;
    private final FileAssetRepository fileAssetRepository;
    private final FilamentRepository filamentRepository;
    private final MakerProjectRepository projectRepository;
    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final ObjectStorageService objectStorageService;
    private final MakerPrintQueueServiceImpl printQueueService;
    private final JsonCodec jsonCodec;

    @Override
    public PageResponse<ArchiveFileResponse> listFiles(
            String query,
            FileFormat3D format,
            MaterialType material,
            String projectGuid,
            Pageable pageable) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<MakerArchiveFile> allFiles = archiveFileRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE);
        if (allFiles.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Long targetProjectId = null;
        if (projectGuid != null && !projectGuid.isBlank()) {
            MakerProject project = projectRepository.findByGuidAndStatus(projectGuid.trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new NotFoundException(MessageCd.PROJECT_NOT_FOUND));
            if (!Objects.equals(company.getId(), project.getCompanyId())) {
                throw new ForbiddenException();
            }
            targetProjectId = project.getId();
        }
        Long filterProjectId = targetProjectId;
        String normalizedQuery = query != null && !query.isBlank() ? query.trim().toLowerCase(Locale.ROOT) : null;

        List<MakerArchiveFile> filtered = allFiles.stream()
                .filter(f -> format == null || format.equals(f.getFormat()))
                .filter(f -> material == null || material.equals(f.getMaterial()))
                .filter(f -> filterProjectId == null || Objects.equals(filterProjectId, f.getMakerProjectId()))
                .filter(f -> matchesQuery(f, normalizedQuery))
                .sorted(buildComparator(pageable))
                .toList();

        Set<Long> filamentIds = filtered.stream().map(MakerArchiveFile::getFilamentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> filamentGuids = filamentIds.isEmpty()
                ? Map.of()
                : filamentRepository.findAllByIdIn(filamentIds).stream()
                .collect(Collectors.toMap(Filament::getId, Filament::getGuid, (a, b) -> a));

        Set<Long> projectIds = filtered.stream().map(MakerArchiveFile::getMakerProjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> projectGuids = projectIds.isEmpty()
                ? Map.of()
                : projectRepository.findAllByIdIn(projectIds).stream()
                .collect(Collectors.toMap(MakerProject::getId, MakerProject::getGuid, (a, b) -> a));

        List<ArchiveFileResponse> responses = filtered.stream()
                .map(file -> toResponse(
                        file,
                        file.getFilamentId() != null ? filamentGuids.get(file.getFilamentId()) : null,
                        file.getMakerProjectId() != null ? projectGuids.get(file.getMakerProjectId()) : null
                ))
                .toList();

        return PageUtils.paginate(responses, pageable);
    }

    @Override
    @Transactional
    public ArchiveFileResponse uploadArchiveFile(MultipartFile file, CreateArchiveFileRequest metadata) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();

        Filament filament = null;
        if (metadata.filamentGuid() != null && !metadata.filamentGuid().isBlank()) {
            filament = filamentRepository.findByGuidAndStatus(metadata.filamentGuid().trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new NotFoundException(MessageCd.FILAMENT_NOT_FOUND));
            if (!Objects.equals(company.getId(), filament.getCompanyId())) {
                throw new ForbiddenException();
            }
        }

        MakerProject project = null;
        if (metadata.projectGuid() != null && !metadata.projectGuid().isBlank()) {
            project = projectRepository.findByGuidAndStatus(metadata.projectGuid().trim(), StatusCd.ACTIVE)
                    .orElseThrow(() -> new NotFoundException(MessageCd.PROJECT_NOT_FOUND));
            if (!Objects.equals(company.getId(), project.getCompanyId())) {
                throw new ForbiddenException();
            }
        }

        FileAsset asset = fileAssetService.upload(file, FileAsset.AssetType.ARCHIVE_3D);

        MakerArchiveFile archiveFile = new MakerArchiveFile();
        archiveFile.setCompanyId(company.getId());
        archiveFile.setFileAssetId(asset.getId());
        archiveFile.setFilamentId(filament != null ? filament.getId() : null);
        archiveFile.setMakerProjectId(project != null ? project.getId() : null);
        archiveFile.setFileName(asset.getName());
        archiveFile.setDisplayName(metadata.displayName().trim());
        archiveFile.setFormat(metadata.format());
        archiveFile.setFileSizeBytes(file.getSize());
        archiveFile.setThumbnailUrl(metadata.thumbnailUrl());
        archiveFile.setMaterial(metadata.material());
        archiveFile.setColorName(CatalogDtoMapper.firstNonBlank(metadata.colorName(), filament != null ? filament.getColorName() : null));
        archiveFile.setColorHex(CatalogDtoMapper.firstNonBlank(metadata.colorHex(), filament != null ? filament.getColorHex() : null));
        archiveFile.setEstimatedWeightGrams(metadata.estimatedWeightGrams());
        archiveFile.setEstimatedTimeMinutes(metadata.estimatedTimeMinutes());
        archiveFile.setLayerHeightMm(metadata.layerHeightMm());
        archiveFile.setInfillPercent(metadata.infillPercent());
        archiveFile.setPlatesCount(metadata.platesCount() != null ? metadata.platesCount() : 1);
        archiveFile.setPrintCount(0);
        archiveFile.setTagsJson(jsonCodec.toJsonArray(metadata.tags()));
        archiveFile.setSourceName(CatalogDtoMapper.firstNonBlank(metadata.sourceName(), "Upload Local"));
        archiveFile.setEstimatedCost(calculateEstimatedCost(metadata, filament, company));

        MakerArchiveFile saved = archiveFileRepository.save(archiveFile);
        return toResponse(
                saved,
                filament != null ? filament.getGuid() : null,
                project != null ? project.getGuid() : null
        );
    }

    @Override
    public ArchiveFileDownloadResponse generateDownloadUrl(String fileGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerArchiveFile archiveFile = requireOwnedArchiveFile(fileGuid, company);

        FileAsset asset = fileAssetRepository.findById(archiveFile.getFileAssetId())
                .orElseThrow(() -> new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND));

        ObjectStorageService.PresignedDownloadUrl presigned =
                objectStorageService.createPresignedDownloadUrl(asset, DOWNLOAD_URL_TTL);

        return new ArchiveFileDownloadResponse(
                archiveFile.getGuid(),
                archiveFile.getFileName(),
                presigned.url(),
                presigned.expiresAt()
        );
    }

    @Override
    public org.imprime.ai.api.service.OrderManagementService.OrderFileContent downloadFileContent(String fileGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerArchiveFile archiveFile = requireOwnedArchiveFile(fileGuid, company);

        FileAsset asset = fileAssetRepository.findById(archiveFile.getFileAssetId())
                .orElseThrow(() -> new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND));

        byte[] bytes = objectStorageService.downloadBytes(asset);
        String fileName = archiveFile.getFileName() != null && !archiveFile.getFileName().isBlank()
                ? archiveFile.getFileName()
                : (asset.getName() != null ? asset.getName() : "modelo.stl");
        String lower = fileName.toLowerCase(Locale.ROOT);
        String contentType = lower.endsWith(".stl") ? "model/stl"
                : lower.endsWith(".3mf") ? "model/3mf"
                : lower.endsWith(".glb") ? "model/gltf-binary"
                : "application/octet-stream";
        return new org.imprime.ai.api.service.OrderManagementService.OrderFileContent(fileName, contentType, bytes);
    }

    @Override
    @Transactional
    public void deleteArchiveFile(String fileGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerArchiveFile archiveFile = requireOwnedArchiveFile(fileGuid, company);
        archiveFile.setStatus(StatusCd.INACTIVE);
        archiveFileRepository.save(archiveFile);
    }

    @Override
    @Transactional
    public PrintQueueItemResponse reprintFile(String fileGuid, ReprintArchiveFileRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        MakerArchiveFile archiveFile = requireOwnedArchiveFile(fileGuid, company);

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

        Filament filament = archiveFile.getFilamentId() != null
                ? filamentRepository.findById(archiveFile.getFilamentId())
                .filter(f -> StatusCd.ACTIVE.equals(f.getStatus()))
                .orElse(null)
                : null;

        MakerProject project = archiveFile.getMakerProjectId() != null
                ? projectRepository.findById(archiveFile.getMakerProjectId())
                .filter(p -> StatusCd.ACTIVE.equals(p.getStatus()))
                .orElse(null)
                : null;

        Order order = null;
        if (request != null && request.orderGuid() != null && !request.orderGuid().isBlank()) {
            order = orderRepository.findOrderByGuid(request.orderGuid().trim())
                    .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
            if (!Objects.equals(company.getId(), order.getVendorId())) {
                throw new ForbiddenException();
            }
        }

        int copies = request != null && request.copies() != null ? request.copies() : 1;
        QueuePriority priority = request != null && request.priority() != null ? request.priority() : QueuePriority.NORMAL;

        PrintQueueItemResponse queueItem = printQueueService.enqueueBuiltJob(
                company,
                printer,
                archiveFile,
                filament,
                order,
                project,
                archiveFile.getDisplayName(),
                archiveFile.getFileName(),
                archiveFile.getMaterial(),
                archiveFile.getColorName(),
                archiveFile.getColorHex(),
                archiveFile.getEstimatedWeightGrams(),
                archiveFile.getEstimatedTimeMinutes(),
                priority,
                copies,
                project != null ? project.getClientName() : null,
                null
        );

        archiveFile.setPrintCount((archiveFile.getPrintCount() != null ? archiveFile.getPrintCount() : 0) + 1);
        archiveFile.setLastPrintedAt(Instant.now());
        archiveFileRepository.save(archiveFile);

        return queueItem;
    }

    private MakerArchiveFile requireOwnedArchiveFile(String fileGuid, Company company) {
        MakerArchiveFile archiveFile = archiveFileRepository.findByGuidAndStatus(fileGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND));
        if (!Objects.equals(company.getId(), archiveFile.getCompanyId())) {
            throw new ForbiddenException();
        }
        return archiveFile;
    }

    private BigDecimal calculateEstimatedCost(
            CreateArchiveFileRequest metadata,
            Filament filament,
            Company company) {
        BigDecimal total = BigDecimal.ZERO;
        boolean hasRate = false;

        if (filament != null && filament.getCostPerKg() != null) {
            BigDecimal materialCost = metadata.estimatedWeightGrams()
                    .divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP)
                    .multiply(filament.getCostPerKg());
            total = total.add(materialCost);
            hasRate = true;
        }

        BigDecimal hours = BigDecimal.valueOf(metadata.estimatedTimeMinutes())
                .divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);

        if (company.getKwhRate() != null) {
            BigDecimal energyCost = hours.multiply(DEFAULT_PRINTER_KW).multiply(company.getKwhRate());
            total = total.add(energyCost);
            hasRate = true;
        }

        if (company.getHourlyDepreciationRate() != null) {
            BigDecimal depCost = hours.multiply(company.getHourlyDepreciationRate());
            total = total.add(depCost);
            hasRate = true;
        }

        return hasRate ? total.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private ArchiveFileResponse toResponse(MakerArchiveFile file, String filamentGuid, String projectGuid) {
        return new ArchiveFileResponse(
                file.getGuid(),
                file.getFileName(),
                file.getDisplayName(),
                file.getFormat(),
                file.getFileSizeBytes() != null ? file.getFileSizeBytes() : 0L,
                file.getThumbnailUrl(),
                file.getMaterial(),
                file.getColorName(),
                file.getColorHex(),
                filamentGuid,
                file.getEstimatedWeightGrams(),
                file.getEstimatedTimeMinutes() != null ? file.getEstimatedTimeMinutes() : 0,
                file.getLayerHeightMm(),
                file.getInfillPercent() != null ? file.getInfillPercent() : 20,
                file.getPlatesCount() != null ? file.getPlatesCount() : 1,
                file.getPrintCount() != null ? file.getPrintCount() : 0,
                file.getLastPrintedAt(),
                file.getCreateDt(),
                jsonCodec.readStringList(file.getTagsJson()),
                file.getSourceName(),
                projectGuid,
                file.getEstimatedCost()
        );
    }

    private boolean matchesQuery(MakerArchiveFile file, String normalizedQuery) {
        if (normalizedQuery == null) {
            return true;
        }
        if (containsIgnoreCase(file.getDisplayName(), normalizedQuery)
                || containsIgnoreCase(file.getFileName(), normalizedQuery)
                || containsIgnoreCase(file.getSourceName(), normalizedQuery)) {
            return true;
        }
        return jsonCodec.readStringList(file.getTagsJson()).stream()
                .anyMatch(tag -> containsIgnoreCase(tag, normalizedQuery));
    }

    private Comparator<MakerArchiveFile> buildComparator(Pageable pageable) {
        Comparator<MakerArchiveFile> defaultComparator = Comparator
                .comparing(MakerArchiveFile::getCreateDt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(MakerArchiveFile::getId, Comparator.nullsLast(Comparator.reverseOrder()));

        if (pageable == null || pageable.getSort().isUnsorted()) {
            return defaultComparator;
        }

        Comparator<MakerArchiveFile> combined = null;
        for (Sort.Order order : pageable.getSort()) {
            Comparator<MakerArchiveFile> next = switch (order.getProperty()) {
                case "createdAt", "createDt" -> Comparator.comparing(
                        MakerArchiveFile::getCreateDt, Comparator.nullsLast(Comparator.naturalOrder()));
                case "displayName" -> Comparator.comparing(
                        MakerArchiveFile::getDisplayName, Comparator.nullsLast(String::compareToIgnoreCase));
                case "fileName" -> Comparator.comparing(
                        MakerArchiveFile::getFileName, Comparator.nullsLast(String::compareToIgnoreCase));
                case "printCount" -> Comparator.comparingInt(f -> f.getPrintCount() != null ? f.getPrintCount() : 0);
                case "lastPrintedAt" -> Comparator.comparing(
                        MakerArchiveFile::getLastPrintedAt, Comparator.nullsLast(Comparator.naturalOrder()));
                case "estimatedTimeMinutes" -> Comparator.comparingInt(
                        f -> f.getEstimatedTimeMinutes() != null ? f.getEstimatedTimeMinutes() : 0);
                case "fileSizeBytes" -> Comparator.comparingLong(
                        f -> f.getFileSizeBytes() != null ? f.getFileSizeBytes() : 0L);
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
