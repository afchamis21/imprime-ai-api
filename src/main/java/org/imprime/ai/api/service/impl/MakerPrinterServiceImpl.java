package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.request.maker.AmsSlotRequest;
import org.imprime.ai.api.http.request.maker.CreatePrinterRequest;
import org.imprime.ai.api.http.request.maker.PrinterCommandRequest;
import org.imprime.ai.api.http.request.maker.PrinterConfigRequest;
import org.imprime.ai.api.http.request.maker.UpdatePrinterRequest;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.AmsSlotDto;
import org.imprime.ai.api.http.response.maker.BuildVolumeDto;
import org.imprime.ai.api.http.response.maker.PrinterConfigDto;
import org.imprime.ai.api.http.response.maker.PrinterResponse;
import org.imprime.ai.api.http.response.maker.PrinterTelemetryDto;
import org.imprime.ai.api.http.response.maker.RegenerateApiKeyResponse;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Filament;
import org.imprime.ai.api.model.PrintQueueItem;
import org.imprime.ai.api.model.PrinterAmsSlot;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.PrintJobStatus;
import org.imprime.ai.api.model.enums.PrinterStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.FilamentRepository;
import org.imprime.ai.api.repo.db.PrintQueueItemRepository;
import org.imprime.ai.api.repo.db.PrinterAmsSlotRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerPrinterService;
import org.imprime.ai.api.util.HashUtil;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Year;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MakerPrinterServiceImpl implements MakerPrinterService {
    private static final int DEFAULT_VOLUME_MM = 256;
    private static final BigDecimal DEFAULT_NOZZLE_MM = new BigDecimal("0.400");
    private static final int DEFAULT_POWER_WATTS = 300;
    private static final Set<PrintJobStatus> RUNNING_JOB_STATUSES = EnumSet.of(
            PrintJobStatus.PRINTING, PrintJobStatus.PAUSED
    );

    private final MakerCompanyResolver makerCompanyResolver;
    private final AssetRepository assetRepository;
    private final PrinterAmsSlotRepository amsSlotRepository;
    private final FilamentRepository filamentRepository;
    private final PrintQueueItemRepository printQueueItemRepository;
    private final JsonCodec jsonCodec;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public PageResponse<PrinterResponse> listPrinters(PrinterStatus status, Pageable pageable) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<Asset> printers = status == null
                ? assetRepository.findAllByCompanyIdAndStatus(company.getId(), StatusCd.ACTIVE)
                : assetRepository.findAllByCompanyIdAndPrinterStatusAndStatus(company.getId(), status, StatusCd.ACTIVE);

        if (printers.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        List<Long> assetIds = printers.stream().map(Asset::getId).toList();
        List<PrinterAmsSlot> allSlots = amsSlotRepository.findAllByAssetIdInAndStatusOrderBySlotNumberAsc(
                assetIds, StatusCd.ACTIVE
        );
        Map<Long, List<PrinterAmsSlot>> slotsByAssetId = allSlots.stream()
                .collect(Collectors.groupingBy(PrinterAmsSlot::getAssetId));

        Set<Long> filamentIds = allSlots.stream()
                .map(PrinterAmsSlot::getFilamentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> filamentGuidsById = filamentIds.isEmpty()
                ? Map.of()
                : filamentRepository.findAllByIdIn(filamentIds).stream()
                .collect(Collectors.toMap(Filament::getId, Filament::getGuid, (a, b) -> a));

        Map<Long, PrintQueueItem> activeJobByAssetId = new HashMap<>();
        for (PrintQueueItem job : printQueueItemRepository.findAllByAssetIdInAndJobStatusInAndStatus(
                assetIds, RUNNING_JOB_STATUSES, StatusCd.ACTIVE)) {
            activeJobByAssetId.putIfAbsent(job.getAssetId(), job);
        }

        List<PrinterResponse> items = printers.stream()
                .sorted(Comparator.comparing(Asset::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(asset -> toResponse(
                        asset,
                        slotsByAssetId.getOrDefault(asset.getId(), List.of()),
                        filamentGuidsById,
                        activeJobByAssetId.get(asset.getId())
                ))
                .toList();

        return PageUtils.paginate(items, pageable);
    }

    @Override
    public PrinterResponse getPrinter(String printerGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Asset asset = requireOwnedPrinter(printerGuid, company);
        return assembleSinglePrinterResponse(asset);
    }

    @Override
    @Transactional
    public PrinterResponse createPrinter(CreatePrinterRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();

        Asset asset = new Asset();
        asset.setCompanyId(company.getId());
        asset.setName(request.name().trim());
        asset.setModel(request.model().trim());
        asset.setMake(extractMake(request.model()));
        asset.setManufactureYear((short) Year.now().getValue());
        asset.setTrayWidth(BigDecimal.valueOf(request.buildVolume() != null ? request.buildVolume().xMm() : DEFAULT_VOLUME_MM));
        asset.setTrayDepth(BigDecimal.valueOf(request.buildVolume() != null ? request.buildVolume().yMm() : DEFAULT_VOLUME_MM));
        asset.setTrayHeight(BigDecimal.valueOf(request.buildVolume() != null ? request.buildVolume().zMm() : DEFAULT_VOLUME_MM));
        asset.setNozzleDiameterMm(request.nozzleDiameterMm() != null ? request.nozzleDiameterMm() : DEFAULT_NOZZLE_MM);
        asset.setPowerWatts(request.powerWatts() != null ? request.powerWatts() : DEFAULT_POWER_WATTS);
        asset.setIpAddress(request.ipAddress());
        asset.setPrinterStatus(PrinterStatus.ONLINE);
        asset.setConfigJson(jsonCodec.toJson(new StoredPrinterConfig(
                false, false, new BigDecimal("0.20"), 20, 36, List.of(), null
        )));
        asset.setTelemetryJson(jsonCodec.toJson(new StoredPrinterTelemetry(
                new BigDecimal("25.0"), BigDecimal.ZERO, new BigDecimal("25.0"), BigDecimal.ZERO,
                null, 0, 0, 0, BigDecimal.ZERO
        )));

        Asset saved = assetRepository.save(asset);
        return assembleSinglePrinterResponse(saved);
    }

    @Override
    @Transactional
    public PrinterResponse updatePrinter(String printerGuid, UpdatePrinterRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Asset asset = requireOwnedPrinter(printerGuid, company);

        if (request.name() != null && !request.name().isBlank()) {
            asset.setName(request.name().trim());
        }
        if (request.model() != null && !request.model().isBlank()) {
            asset.setModel(request.model().trim());
            asset.setMake(extractMake(request.model()));
        }
        if (request.status() != null) {
            asset.setPrinterStatus(request.status());
        }
        if (request.buildVolume() != null) {
            asset.setTrayWidth(BigDecimal.valueOf(request.buildVolume().xMm()));
            asset.setTrayDepth(BigDecimal.valueOf(request.buildVolume().yMm()));
            asset.setTrayHeight(BigDecimal.valueOf(request.buildVolume().zMm()));
        }
        if (request.nozzleDiameterMm() != null) {
            asset.setNozzleDiameterMm(request.nozzleDiameterMm());
        }
        if (request.powerWatts() != null) {
            asset.setPowerWatts(request.powerWatts());
        }
        if (request.ipAddress() != null) {
            asset.setIpAddress(request.ipAddress());
        }
        if (request.config() != null) {
            StoredPrinterConfig merged = mergeConfig(readConfig(asset), request.config(), company);
            asset.setConfigJson(jsonCodec.toJson(merged));
        }

        Asset saved = assetRepository.save(asset);
        if (request.amsSlots() != null) {
            synchronizeAmsSlots(saved, request.amsSlots(), company);
        }

        return assembleSinglePrinterResponse(saved);
    }

    @Override
    @Transactional
    public void deletePrinter(String printerGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Asset asset = requireOwnedPrinter(printerGuid, company);

        boolean busyStatus = PrinterStatus.PRINTING.equals(asset.getPrinterStatus())
                || PrinterStatus.PAUSED.equals(asset.getPrinterStatus());
        boolean hasRunningJobs = printQueueItemRepository.existsByAssetIdAndJobStatusInAndStatus(
                asset.getId(), RUNNING_JOB_STATUSES, StatusCd.ACTIVE
        );
        if (busyStatus || hasRunningJobs) {
            throw new ConflictException(MessageCd.PRINTER_BUSY);
        }

        asset.setStatus(StatusCd.INACTIVE);
        assetRepository.save(asset);
    }

    @Override
    @Transactional
    public RegenerateApiKeyResponse regenerateApiKey(String printerGuid) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Asset asset = requireOwnedPrinter(printerGuid, company);

        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        String rawApiKey = "iak_live_" + HexFormat.of().formatHex(randomBytes);
        String prefix = rawApiKey.substring(0, 16) + "****";

        asset.setApiKeyHash(HashUtil.sha256(rawApiKey));
        asset.setApiKeyPrefix(prefix);
        assetRepository.save(asset);

        return new RegenerateApiKeyResponse(asset.getGuid(), rawApiKey, prefix, Instant.now());
    }

    @Override
    @Transactional
    public PrinterResponse sendCommand(String printerGuid, PrinterCommandRequest request) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        Asset asset = requireOwnedPrinter(printerGuid, company);
        PrinterStatus currentStatus = asset.getPrinterStatus() != null ? asset.getPrinterStatus() : PrinterStatus.ONLINE;
        StoredTelemetryHolder telemetryHolder = new StoredTelemetryHolder(readTelemetry(asset));

        switch (request.action()) {
            case PAUSE -> {
                if (!PrinterStatus.PRINTING.equals(currentStatus)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                asset.setPrinterStatus(PrinterStatus.PAUSED);
                updateActiveJobsStatus(asset.getId(), PrintJobStatus.PRINTING, PrintJobStatus.PAUSED);
            }
            case RESUME -> {
                if (!PrinterStatus.PAUSED.equals(currentStatus)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                asset.setPrinterStatus(PrinterStatus.PRINTING);
                updateActiveJobsStatus(asset.getId(), PrintJobStatus.PAUSED, PrintJobStatus.PRINTING);
            }
            case STOP -> {
                if (!PrinterStatus.PRINTING.equals(currentStatus) && !PrinterStatus.PAUSED.equals(currentStatus)) {
                    throw new ConflictException(MessageCd.CONFLICT);
                }
                asset.setPrinterStatus(PrinterStatus.ONLINE);
                updateActiveJobsStatus(asset.getId(), null, PrintJobStatus.CANCELLED);
                telemetryHolder.setTargets(BigDecimal.ZERO, BigDecimal.ZERO, 0);
            }
            case PREHEAT -> {
                if (PrinterStatus.PRINTING.equals(currentStatus)
                        || PrinterStatus.OFFLINE.equals(currentStatus)
                        || PrinterStatus.MAINTENANCE.equals(currentStatus)) {
                    throw new ConflictException(MessageCd.PRINTER_BUSY);
                }
                telemetryHolder.setTargets(new BigDecimal("210.0"), new BigDecimal("60.0"), 0);
            }
            case COOLDOWN -> {
                if (PrinterStatus.PRINTING.equals(currentStatus)) {
                    throw new ConflictException(MessageCd.PRINTER_BUSY);
                }
                telemetryHolder.setTargets(BigDecimal.ZERO, BigDecimal.ZERO, 100);
            }
            case SET_ONLINE -> {
                ensureNotPrintingOrPaused(currentStatus);
                asset.setPrinterStatus(PrinterStatus.ONLINE);
            }
            case SET_OFFLINE -> {
                ensureNotPrintingOrPaused(currentStatus);
                asset.setPrinterStatus(PrinterStatus.OFFLINE);
            }
            case SET_MAINTENANCE -> {
                ensureNotPrintingOrPaused(currentStatus);
                asset.setPrinterStatus(PrinterStatus.MAINTENANCE);
            }
        }

        asset.setTelemetryJson(jsonCodec.toJson(telemetryHolder.value()));
        Asset saved = assetRepository.save(asset);
        return assembleSinglePrinterResponse(saved);
    }

    public static Asset selectEligiblePrinter(List<Asset> companyPrinters) {
        if (companyPrinters == null || companyPrinters.isEmpty()) {
            throw new NotFoundException(MessageCd.PRINTER_NOT_FOUND);
        }
        return companyPrinters.stream()
                .filter(p -> PrinterStatus.ONLINE.equals(p.getPrinterStatus()))
                .findFirst()
                .or(() -> companyPrinters.stream()
                        .filter(p -> !PrinterStatus.OFFLINE.equals(p.getPrinterStatus())
                                && !PrinterStatus.MAINTENANCE.equals(p.getPrinterStatus())
                                && !PrinterStatus.ERROR.equals(p.getPrinterStatus()))
                        .findFirst())
                .or(() -> companyPrinters.stream().findFirst())
                .orElseThrow(() -> new NotFoundException(MessageCd.PRINTER_NOT_FOUND));
    }

    private Asset requireOwnedPrinter(String printerGuid, Company company) {
        Asset asset = assetRepository.findByGuidAndStatus(printerGuid, StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.PRINTER_NOT_FOUND));
        if (!Objects.equals(company.getId(), asset.getCompanyId())) {
            throw new ForbiddenException();
        }
        return asset;
    }

    private void ensureNotPrintingOrPaused(PrinterStatus currentStatus) {
        if (PrinterStatus.PRINTING.equals(currentStatus) || PrinterStatus.PAUSED.equals(currentStatus)) {
            throw new ConflictException(MessageCd.PRINTER_BUSY);
        }
    }

    private void updateActiveJobsStatus(Long assetId, PrintJobStatus expectedCurrent, PrintJobStatus targetStatus) {
        List<PrintQueueItem> runningJobs = printQueueItemRepository.findAllByAssetIdAndJobStatusInAndStatus(
                assetId, RUNNING_JOB_STATUSES, StatusCd.ACTIVE
        );
        for (PrintQueueItem job : runningJobs) {
            if (expectedCurrent == null || expectedCurrent.equals(job.getJobStatus())) {
                job.setJobStatus(targetStatus);
                printQueueItemRepository.save(job);
            }
        }
    }

    private void synchronizeAmsSlots(Asset asset, List<AmsSlotRequest> slotRequests, Company company) {
        Set<Integer> seenSlots = new HashSet<>();
        for (AmsSlotRequest slotReq : slotRequests) {
            if (!seenSlots.add(slotReq.slotNumber())) {
                throw new BadRequestException(MessageCd.VALIDATION_ERROR);
            }
        }

        List<PrinterAmsSlot> existing = amsSlotRepository.findAllByAssetIdAndStatusOrderBySlotNumberAsc(
                asset.getId(), StatusCd.ACTIVE
        );
        Map<Integer, PrinterAmsSlot> existingBySlot = existing.stream()
                .collect(Collectors.toMap(PrinterAmsSlot::getSlotNumber, s -> s, (a, b) -> a));

        for (PrinterAmsSlot oldSlot : existing) {
            if (!seenSlots.contains(oldSlot.getSlotNumber())) {
                oldSlot.setStatus(StatusCd.INACTIVE);
                amsSlotRepository.save(oldSlot);
            }
        }

        for (AmsSlotRequest req : slotRequests) {
            Long filamentId = null;
            if (req.filamentGuid() != null && !req.filamentGuid().isBlank()) {
                Filament filament = filamentRepository.findByGuidAndStatus(req.filamentGuid().trim(), StatusCd.ACTIVE)
                        .orElseThrow(() -> new NotFoundException(MessageCd.FILAMENT_NOT_FOUND));
                if (!Objects.equals(company.getId(), filament.getCompanyId())) {
                    throw new ForbiddenException();
                }
                filamentId = filament.getId();
            }

            PrinterAmsSlot slot = existingBySlot.getOrDefault(req.slotNumber(), new PrinterAmsSlot());
            slot.setAssetId(asset.getId());
            slot.setSlotNumber(req.slotNumber());
            slot.setFilamentId(filamentId);
            slot.setLabel(req.label());
            slot.setStatus(StatusCd.ACTIVE);
            amsSlotRepository.save(slot);
        }
    }

    private StoredPrinterConfig mergeConfig(StoredPrinterConfig current, PrinterConfigRequest req, Company company) {
        List<String> linkedFilaments = current.linkedFilamentGuids();
        if (req.linkedFilamentGuids() != null) {
            for (String fGuid : req.linkedFilamentGuids()) {
                Filament filament = filamentRepository.findByGuidAndStatus(fGuid, StatusCd.ACTIVE)
                        .orElseThrow(() -> new NotFoundException(MessageCd.FILAMENT_NOT_FOUND));
                if (!Objects.equals(company.getId(), filament.getCompanyId())) {
                    throw new ForbiddenException();
                }
            }
            linkedFilaments = List.copyOf(req.linkedFilamentGuids());
        }

        return new StoredPrinterConfig(
                req.autoAcceptOrders() != null ? req.autoAcceptOrders() : current.autoAcceptOrders(),
                req.apiEnabled() != null ? req.apiEnabled() : current.apiEnabled(),
                req.maxLayerHeightMm() != null ? req.maxLayerHeightMm() : current.maxLayerHeightMm(),
                req.defaultInfillPercent() != null ? req.defaultInfillPercent() : current.defaultInfillPercent(),
                req.maxPrintTimeHours() != null ? req.maxPrintTimeHours() : current.maxPrintTimeHours(),
                linkedFilaments,
                req.notes() != null ? req.notes() : current.notes()
        );
    }

    private PrinterResponse assembleSinglePrinterResponse(Asset asset) {
        List<PrinterAmsSlot> slots = amsSlotRepository.findAllByAssetIdAndStatusOrderBySlotNumberAsc(
                asset.getId(), StatusCd.ACTIVE
        );
        Set<Long> filamentIds = slots.stream()
                .map(PrinterAmsSlot::getFilamentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> filamentGuids = filamentIds.isEmpty()
                ? Map.of()
                : filamentRepository.findAllByIdIn(filamentIds).stream()
                .collect(Collectors.toMap(Filament::getId, Filament::getGuid, (a, b) -> a));

        PrintQueueItem activeJob = printQueueItemRepository.findAllByAssetIdAndJobStatusInAndStatus(
                asset.getId(), RUNNING_JOB_STATUSES, StatusCd.ACTIVE
        ).stream().findFirst().orElse(null);

        return toResponse(asset, slots, filamentGuids, activeJob);
    }

    private PrinterResponse toResponse(
            Asset asset,
            List<PrinterAmsSlot> slots,
            Map<Long, String> filamentGuidsById,
            PrintQueueItem activeJob) {
        BuildVolumeDto volume = new BuildVolumeDto(
                asset.getTrayWidth() != null ? asset.getTrayWidth().intValue() : DEFAULT_VOLUME_MM,
                asset.getTrayDepth() != null ? asset.getTrayDepth().intValue() : DEFAULT_VOLUME_MM,
                asset.getTrayHeight() != null ? asset.getTrayHeight().intValue() : DEFAULT_VOLUME_MM
        );

        List<AmsSlotDto> slotDtos = slots.stream()
                .map(slot -> new AmsSlotDto(
                        slot.getSlotNumber(),
                        slot.getFilamentId() != null ? filamentGuidsById.get(slot.getFilamentId()) : null,
                        slot.getLabel()
                ))
                .toList();

        StoredPrinterTelemetry storedTelemetry = readTelemetry(asset);
        Integer etaMinutes = computeEtaMinutes(activeJob);
        PrinterTelemetryDto telemetryDto = new PrinterTelemetryDto(
                storedTelemetry.nozzleTempC(),
                storedTelemetry.nozzleTargetC(),
                storedTelemetry.bedTempC(),
                storedTelemetry.bedTargetC(),
                storedTelemetry.chamberTempC(),
                storedTelemetry.fanSpeedPercent(),
                activeJob != null ? activeJob.getGuid() : null,
                activeJob != null ? activeJob.getTitle() : null,
                activeJob != null ? activeJob.getProgressPercent() : null,
                etaMinutes,
                storedTelemetry.currentLayer(),
                storedTelemetry.totalLayers(),
                storedTelemetry.totalPrintHours()
        );

        StoredPrinterConfig storedConfig = readConfig(asset);
        boolean apiKeyConfigured = asset.getApiKeyHash() != null && !asset.getApiKeyHash().isBlank();
        PrinterConfigDto configDto = new PrinterConfigDto(
                Boolean.TRUE.equals(storedConfig.autoAcceptOrders()),
                Boolean.TRUE.equals(storedConfig.apiEnabled()),
                apiKeyConfigured,
                asset.getApiKeyPrefix(),
                storedConfig.maxLayerHeightMm(),
                storedConfig.defaultInfillPercent() != null ? storedConfig.defaultInfillPercent() : 20,
                storedConfig.maxPrintTimeHours() != null ? storedConfig.maxPrintTimeHours() : 36,
                storedConfig.linkedFilamentGuids() != null ? storedConfig.linkedFilamentGuids() : List.of(),
                storedConfig.notes()
        );

        return new PrinterResponse(
                asset.getGuid(),
                asset.getName(),
                asset.getModel(),
                asset.getPrinterStatus() != null ? asset.getPrinterStatus() : PrinterStatus.ONLINE,
                volume,
                asset.getNozzleDiameterMm(),
                asset.getPowerWatts(),
                asset.getIpAddress(),
                asset.getSerialNumber(),
                slotDtos,
                telemetryDto,
                configDto
        );
    }

    private Integer computeEtaMinutes(PrintQueueItem activeJob) {
        if (activeJob == null || activeJob.getEstimatedTimeMinutes() == null) {
            return null;
        }
        int totalMinutes = activeJob.getEstimatedTimeMinutes();
        int progress = activeJob.getProgressPercent() != null ? activeJob.getProgressPercent() : 0;
        int remaining = (int) Math.round(totalMinutes * Math.max(0, 100 - progress) / 100.0);
        return Math.max(0, remaining);
    }

    private StoredPrinterConfig readConfig(Asset asset) {
        StoredPrinterConfig parsed = jsonCodec.readObject(asset.getConfigJson(), StoredPrinterConfig.class);
        if (parsed != null) {
            return parsed;
        }
        return new StoredPrinterConfig(false, false, new BigDecimal("0.20"), 20, 36, List.of(), null);
    }

    private StoredPrinterTelemetry readTelemetry(Asset asset) {
        StoredPrinterTelemetry parsed = jsonCodec.readObject(asset.getTelemetryJson(), StoredPrinterTelemetry.class);
        if (parsed != null) {
            return parsed;
        }
        return new StoredPrinterTelemetry(
                new BigDecimal("25.0"), BigDecimal.ZERO, new BigDecimal("25.0"), BigDecimal.ZERO,
                null, 0, 0, 0, BigDecimal.ZERO
        );
    }

    private String extractMake(String model) {
        String trimmed = model.trim();
        int spaceIdx = trimmed.indexOf(' ');
        return spaceIdx > 0 ? trimmed.substring(0, spaceIdx) : trimmed;
    }

    public record StoredPrinterConfig(
            Boolean autoAcceptOrders,
            Boolean apiEnabled,
            BigDecimal maxLayerHeightMm,
            Integer defaultInfillPercent,
            Integer maxPrintTimeHours,
            List<String> linkedFilamentGuids,
            String notes
    ) {
    }

    public record StoredPrinterTelemetry(
            BigDecimal nozzleTempC,
            BigDecimal nozzleTargetC,
            BigDecimal bedTempC,
            BigDecimal bedTargetC,
            BigDecimal chamberTempC,
            Integer fanSpeedPercent,
            Integer currentLayer,
            Integer totalLayers,
            BigDecimal totalPrintHours
    ) {
    }

    private static final class StoredTelemetryHolder {
        private StoredPrinterTelemetry value;

        private StoredTelemetryHolder(StoredPrinterTelemetry initial) {
            this.value = initial;
        }

        private void setTargets(BigDecimal nozzleTarget, BigDecimal bedTarget, Integer fanSpeed) {
            this.value = new StoredPrinterTelemetry(
                    value.nozzleTempC(),
                    nozzleTarget,
                    value.bedTempC(),
                    bedTarget,
                    value.chamberTempC(),
                    fanSpeed != null ? fanSpeed : value.fanSpeedPercent(),
                    value.currentLayer(),
                    value.totalLayers(),
                    value.totalPrintHours()
            );
        }

        private StoredPrinterTelemetry value() {
            return value;
        }
    }
}
