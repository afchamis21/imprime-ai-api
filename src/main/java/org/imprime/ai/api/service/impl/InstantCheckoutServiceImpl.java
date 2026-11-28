package org.imprime.ai.api.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.SlicingValidationProperties;
import org.imprime.ai.api.config.SlicingValidationProperties.ExecutionMode;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.InstantCheckoutRequest;
import org.imprime.ai.api.http.request.order.InstantCheckoutRequest.PlateCheckoutInput;
import org.imprime.ai.api.http.response.order.InstantCheckoutResponse;
import org.imprime.ai.api.http.response.order.SlicingValidationStatusResponse;
import org.imprime.ai.api.http.response.order.SlicingValidationStatusResponse.PlateValidationDetail;
import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Asset;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.MakerMaterialPrice;
import org.imprime.ai.api.model.MakerPricingProfile;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.PrinterCatalog;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderPricingSource;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.SlicingValidationStatus;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadGatewayException;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.model.exception.UnprocessableEntityException;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.AssetRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.MakerMaterialPriceRepository;
import org.imprime.ai.api.repo.db.MakerPricingProfileRepository;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.PrinterCatalogRepository;
import org.imprime.ai.api.service.FileAssetService;
import org.imprime.ai.api.service.InstantCheckoutService;
import org.imprime.ai.api.service.LegalDocumentService;
import org.imprime.ai.api.service.MakerEligibilityService;
import org.imprime.ai.api.service.MakerEligibilityService.InstantQuoteMatchResult;
import org.imprime.ai.api.service.MakerEligibilityService.MatchedPlatePrinter;
import org.imprime.ai.api.service.MakerEligibilityService.PlateMatchSpec;
import org.imprime.ai.api.service.MarketplaceQuoteCalculator;
import org.imprime.ai.api.service.MarketplaceQuoteCalculator.CalculatedOrderQuote;
import org.imprime.ai.api.service.MarketplaceQuoteCalculator.PlateQuoteInput;
import org.imprime.ai.api.service.ObjectStorageService;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.service.OrderUploadRequestService;
import org.imprime.ai.api.service.PrinterSpeedResolver;
import org.imprime.ai.api.slicing.BambuProjectPacker;
import org.imprime.ai.api.slicing.BambuProjectPacker.PackedProjectResult;
import org.imprime.ai.api.slicing.HeadlessSlicerEngine.HeadlessSliceRequest;
import org.imprime.ai.api.slicing.HeadlessSlicerEngine.HeadlessSliceResult;
import org.imprime.ai.api.slicing.HeadlessSlicerEngineRouter;
import org.imprime.ai.api.slicing.SlicedArtifactStorage;
import org.imprime.ai.api.slicing.SlicingReferenceProfileResolver;
import org.imprime.ai.api.slicing.SlicingReferenceProfileResolver.ProcessParameters;
import org.imprime.ai.api.slicing.SlicingReferenceProfileResolver.ResolvedPresetPaths;
import org.imprime.ai.api.slicing.SlicingValidationComparator;
import org.imprime.ai.api.slicing.SlicingValidationComparator.OrderComparisonSummary;
import org.imprime.ai.api.slicing.SlicingValidationComparator.PlateComparisonInput;
import org.imprime.ai.api.slicing.SlicingValidationComparator.PlateComparisonResult;
import org.imprime.ai.api.slicing.SlicingValidationQueueMessage;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.validator.PrintFileValidator;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InstantCheckoutServiceImpl implements InstantCheckoutService {

    private static final String UPLOAD_SCOPE = "INSTANT_CHECKOUT";

    private final SlicingValidationProperties properties;
    private final PrintFileValidator printFileValidator;
    private final LegalDocumentService legalDocuments;
    private final OrderUploadRequestService uploadRequests;
    private final AddressRepository addressRepository;
    private final CompanyRepository companyRepository;
    private final MakerPricingProfileRepository pricingProfileRepository;
    private final MakerMaterialPriceRepository materialPriceRepository;
    private final AssetRepository assetRepository;
    private final PrinterCatalogRepository printerCatalogRepository;
    private final FileAssetService fileAssetService;
    private final FileAssetRepository fileAssetRepository;
    private final ObjectStorageService objectStorageService;
    private final OrderRepository orderRepository;
    private final OrderFileAssetRepository orderFileAssetRepository;
    private final OrderStatusService orderStatusService;
    private final MakerEligibilityService makerEligibilityService;
    private final PrinterSpeedResolver printerSpeedResolver;
    private final MarketplaceQuoteCalculator quoteCalculator;
    private final BambuProjectPacker projectPacker;
    private final SlicingReferenceProfileResolver profileResolver;
    private final HeadlessSlicerEngineRouter slicerRouter;
    private final SlicingValidationComparator validationComparator;
    private final SlicedArtifactStorage artifactStorage;
    private final JsonCodec jsonCodec;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final AtomicReference<Semaphore> executionSemaphore = new AtomicReference<>();

    public InstantCheckoutServiceImpl(
            SlicingValidationProperties properties,
            PrintFileValidator printFileValidator,
            LegalDocumentService legalDocuments,
            OrderUploadRequestService uploadRequests,
            AddressRepository addressRepository,
            CompanyRepository companyRepository,
            MakerPricingProfileRepository pricingProfileRepository,
            MakerMaterialPriceRepository materialPriceRepository,
            AssetRepository assetRepository,
            PrinterCatalogRepository printerCatalogRepository,
            FileAssetService fileAssetService,
            FileAssetRepository fileAssetRepository,
            ObjectStorageService objectStorageService,
            OrderRepository orderRepository,
            OrderFileAssetRepository orderFileAssetRepository,
            OrderStatusService orderStatusService,
            MakerEligibilityService makerEligibilityService,
            PrinterSpeedResolver printerSpeedResolver,
            MarketplaceQuoteCalculator quoteCalculator,
            BambuProjectPacker projectPacker,
            SlicingReferenceProfileResolver profileResolver,
            HeadlessSlicerEngineRouter slicerRouter,
            SlicingValidationComparator validationComparator,
            SlicedArtifactStorage artifactStorage,
            JsonCodec jsonCodec,
            ObjectProvider<RabbitTemplate> rabbitTemplateProvider
    ) {
        this.properties = properties;
        this.printFileValidator = printFileValidator;
        this.legalDocuments = legalDocuments;
        this.uploadRequests = uploadRequests;
        this.addressRepository = addressRepository;
        this.companyRepository = companyRepository;
        this.pricingProfileRepository = pricingProfileRepository;
        this.materialPriceRepository = materialPriceRepository;
        this.assetRepository = assetRepository;
        this.printerCatalogRepository = printerCatalogRepository;
        this.fileAssetService = fileAssetService;
        this.fileAssetRepository = fileAssetRepository;
        this.objectStorageService = objectStorageService;
        this.orderRepository = orderRepository;
        this.orderFileAssetRepository = orderFileAssetRepository;
        this.orderStatusService = orderStatusService;
        this.makerEligibilityService = makerEligibilityService;
        this.printerSpeedResolver = printerSpeedResolver;
        this.quoteCalculator = quoteCalculator;
        this.projectPacker = projectPacker;
        this.profileResolver = profileResolver;
        this.slicerRouter = slicerRouter;
        this.validationComparator = validationComparator;
        this.artifactStorage = artifactStorage;
        this.jsonCodec = jsonCodec;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
    }

    @Override
    @Transactional(noRollbackFor = {UnprocessableEntityException.class, BadGatewayException.class})
    public InstantCheckoutResponse createAndValidate(
            List<MultipartFile> files,
            InstantCheckoutRequest request,
            @Nullable String idempotencyKey
    ) {
        User buyer = ServiceContext.getUserOrThrow();
        if (!Boolean.TRUE.equals(request.rightsDeclared())) {
            throw new BadRequestException(MessageCd.PRINT_DECLARATION_REQUIRED);
        }

        long declarationDocId = legalDocuments.requireAcceptableVersion("print-declaration", request.declarationVersion());
        long prohibitedDocId = legalDocuments.requireAcceptableVersion("prohibited-items", request.prohibitedItemsVersion());

        if (files == null || files.isEmpty() || files.size() != request.plates().size()) {
            throw new BadRequestException("O número de arquivos enviados deve corresponder às mesas informadas.", MessageCd.VALIDATION_ERROR);
        }
        printFileValidator.validate(files);

        UUID requestId = parseUuid(idempotencyKey);
        if (requestId != null) {
            String replayOrderGuid = uploadRequests.reserveOrReplay(buyer.getId(), requestId, UPLOAD_SCOPE);
            if (replayOrderGuid != null) {
                SlicingValidationStatusResponse existing = getValidationStatus(replayOrderGuid);
                Order replayOrder = orderRepository.findOrderByGuid(replayOrderGuid).orElse(null);
                Company replayMaker = replayOrder != null && replayOrder.getVendorId() != null
                        ? companyRepository.findById(replayOrder.getVendorId()).orElse(null)
                        : null;
                return InstantCheckoutResponse.fromStatus(
                        existing,
                        replayMaker != null ? replayMaker.getGuid() : request.makerGuid(),
                        replayMaker != null ? replayMaker.getName() : null
                );
            }
        }

        Address address = addressRepository.findByGuidAndStatus(request.addressGuid().trim(), StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.ADDRESS_NOT_FOUND));
        if (address.getOwnerType() != EntityType.USER || !Objects.equals(address.getOwnerId(), buyer.getId())) {
            throw new ForbiddenException();
        }

        Company maker = companyRepository.findByGuidAndStatus(request.makerGuid().trim(), StatusCd.ACTIVE)
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));

        MakerPricingProfile profile = loadMakerPricingProfile(maker);
        if (!Boolean.TRUE.equals(profile.getInstantQuoteEnabled())) {
            throw new ConflictException(MessageCd.MAKER_NOT_ELIGIBLE_FOR_QUOTE);
        }

        Map<MaterialType, BigDecimal> materialPrices = loadMakerMaterialPrices(maker.getId());
        List<Asset> fleet = assetRepository.findAllByCompanyIdAndStatus(maker.getId(), StatusCd.ACTIVE);
        Map<Long, PrinterCatalog> catalogsById = loadCatalogsForFleet(fleet);

        // Pre-pack and sanitize all 3D models to derive authoritative geometry bounds & strip malicious content
        List<PackedPlateSource> packedSources = new ArrayList<>(files.size());
        long totalBytes = 0L;
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            PlateCheckoutInput plateInput = request.plates().get(i);
            byte[] rawBytes;
            try {
                rawBytes = file.getBytes();
            } catch (IOException e) {
                throw new BadRequestException(MessageCd.FILE_EMPTY);
            }
            totalBytes += rawBytes.length;

            double sx = positiveOrOne(plateInput.scaleX());
            double sy = positiveOrOne(plateInput.scaleY());
            double sz = positiveOrOne(plateInput.scaleZ());
            PackedProjectResult packed = projectPacker.packClean3mf(
                    file.getOriginalFilename(),
                    rawBytes,
                    sx,
                    sy,
                    sz,
                    properties.getMaxTrianglesPerPlate()
            );

            MaterialType material = MakerEligibilityService.parseMaterial(plateInput.material());
            if (material == null) {
                throw new BadRequestException("Material inválido na mesa " + (i + 1), MessageCd.VALIDATION_ERROR);
            }
            packedSources.add(new PackedPlateSource(i, file, rawBytes, plateInput, material, packed));
        }

        List<PlateMatchSpec> matchSpecs = packedSources.stream()
                .map(ps -> new PlateMatchSpec(
                        resolvedPlateNumber(ps.input(), ps.index()),
                        ps.material(),
                        ps.packed().widthMm(),
                        ps.packed().depthMm(),
                        ps.packed().heightMm(),
                        Boolean.TRUE.equals(ps.input().isMultiColor()),
                        ps.input().colorCount() != null ? ps.input().colorCount() : 1
                ))
                .toList();

        InstantQuoteMatchResult printerMatch = makerEligibilityService.matchPrintersForInstantQuote(
                maker,
                fleet,
                catalogsById,
                matchSpecs,
                materialPrices.keySet(),
                printerSpeedResolver
        );
        if (!printerMatch.eligible() || printerMatch.primaryPrinter() == null) {
            throw new ConflictException(MessageCd.MAKER_NOT_ELIGIBLE_FOR_QUOTE);
        }

        List<PlateQuoteInput> clientQuoteInputs = new ArrayList<>(packedSources.size());
        for (PackedPlateSource ps : packedSources) {
            int plateNum = resolvedPlateNumber(ps.input(), ps.index());
            MatchedPlatePrinter mp = printerMatch.plateMatches().get(plateNum);
            clientQuoteInputs.add(new PlateQuoteInput(
                    plateNum,
                    resolvePlateName(ps.input(), ps.file(), ps.index()),
                    ps.material(),
                    ps.input().quantity(),
                    ps.input().clientModelWeightGrams(),
                    ps.input().clientSupportWeightGrams() != null ? ps.input().clientSupportWeightGrams() : BigDecimal.ZERO,
                    ps.input().clientBasePrintTimeSeconds(),
                    ps.input().finish(),
                    ps.input().urgency(),
                    mp.assetId(),
                    mp.printerGuid(),
                    mp.printerName(),
                    mp.printerModel(),
                    mp.speedTier(),
                    mp.speedMultiplier()
            ));
        }
        CalculatedOrderQuote clientCalculated = quoteCalculator.calculate(profile, materialPrices, clientQuoteInputs);
        BigDecimal clientQuotedAmount = request.expectedTotalPriceBrl() != null && request.expectedTotalPriceBrl().signum() > 0
                ? request.expectedTotalPriceBrl().setScale(2, RoundingMode.HALF_UP)
                : clientCalculated.totalPriceBrl();

        List<FileAsset> uploadedAssets = new ArrayList<>(packedSources.size());
        try {
            for (PackedPlateSource ps : packedSources) {
                uploadedAssets.add(fileAssetService.uploadOrderModel(ps.file()));
            }
        } catch (RuntimeException ex) {
            cleanupUploadedAssets(uploadedAssets);
            throw ex;
        }

        MatchedPlatePrinter primary = printerMatch.primaryPrinter();
        Order order = new Order();
        order.setBuyerId(buyer.getId());
        order.setVendorId(maker.getId());
        order.setToAddressId(address.getId());
        order.setFileAssetId(uploadedAssets.getFirst().getId());
        order.setPricingSource(OrderPricingSource.INSTANT_QUOTE);
        order.setSelectedPrinterAssetId(primary.assetId());
        order.setSelectedPrinterName(truncate(primary.printerName(), 120));
        order.setSelectedSpeedTier(primary.speedTier());
        order.setSelectedSpeedMultiplier(primary.speedMultiplier());
        order.setEstimatedPrintTimeSeconds(clientCalculated.totalEstimatedPrintTimeSeconds());
        order.setProductionDays(clientCalculated.estimatedLeadTimeDays());
        order.setClientQuotedAmount(clientQuotedAmount);
        order.setSlicingValidationStatus(SlicingValidationStatus.PENDING);
        order.setStatus(StatusCd.ACTIVE);
        order = orderRepository.save(order);

        if (order.getOrderCode() == null || order.getOrderCode().isBlank()) {
            order.setOrderCode("PED-" + order.getGuid().replace("-", "").substring(0, 6).toUpperCase());
            order = orderRepository.save(order);
        }

        String engineId = hasText(request.slicerEngineId()) ? request.slicerEngineId().trim() : SlicingReferenceProfileResolver.REFERENCE_ENGINE_ID;
        String profileId = hasText(request.slicerProfileId()) ? request.slicerProfileId().trim() : SlicingReferenceProfileResolver.REFERENCE_PROFILE_ID;

        List<OrderFileAsset> plateEntities = new ArrayList<>(packedSources.size());
        for (PackedPlateSource ps : packedSources) {
            FileAsset asset = uploadedAssets.get(ps.index());
            legalDocuments.recordOrderFileDeclaration(
                    order.getId(),
                    asset.getId(),
                    buyer.getId(),
                    declarationDocId,
                    prohibitedDocId,
                    sha256Hex(ps.rawBytes())
            );
            OrderFileAsset entity = buildOrderFileAsset(
                    order.getId(),
                    asset,
                    ps,
                    engineId,
                    profileId,
                    printerMatch.plateMatches().get(resolvedPlateNumber(ps.input(), ps.index()))
            );
            plateEntities.add(entity);
        }
        plateEntities = orderFileAssetRepository.saveAll(plateEntities);

        orderStatusService.move(order, OrderStatusCd.CREATED);
        orderStatusService.move(order, OrderStatusCd.QUOTING);
        if (requestId != null) {
            uploadRequests.finish(buyer.getId(), requestId, order.getGuid());
        }

        if (!properties.isEnabled()) {
            finalizeWithClientMetricsWhenValidationDisabled(order, plateEntities, clientCalculated);
            return InstantCheckoutResponse.fromStatus(buildStatusResponse(order, plateEntities), maker.getGuid(), maker.getName());
        }

        ExecutionMode mode = properties.getExecutionMode();
        Semaphore semaphore = semaphore();
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider != null ? rabbitTemplateProvider.getIfAvailable() : null;

        boolean acquiredSyncSlot = false;
        boolean runSyncNow = false;
        if (mode == ExecutionMode.SYNC) {
            acquiredSyncSlot = acquireBlocking(semaphore);
            runSyncNow = true;
        } else if (mode == ExecutionMode.HYBRID
                && packedSources.size() <= properties.getSyncMaxPlates()
                && totalBytes <= properties.getSyncMaxBytes()) {
            acquiredSyncSlot = semaphore.tryAcquire();
            runSyncNow = acquiredSyncSlot || rabbitTemplate == null;
            if (!acquiredSyncSlot && runSyncNow) {
                acquiredSyncSlot = acquireBlocking(semaphore);
            }
        } else if (rabbitTemplate == null) {
            acquiredSyncSlot = acquireBlocking(semaphore);
            runSyncNow = true;
        }

        if (!runSyncNow) {
            enqueueValidationAfterCommit(rabbitTemplate, order.getGuid(), buyer.getId());
            return InstantCheckoutResponse.fromStatus(
                    buildStatusResponse(order, plateEntities),
                    maker.getGuid(),
                    maker.getName()
            );
        }

        try {
            Map<Integer, PackedProjectResult> prePackedByPlate = packedSources.stream()
                    .collect(Collectors.toMap(
                            ps -> resolvedPlateNumber(ps.input(), ps.index()),
                            PackedPlateSource::packed
                    ));
            validateAndFinalizeOrder(order, plateEntities, maker, prePackedByPlate);
            return InstantCheckoutResponse.fromStatus(
                    buildStatusResponse(order, plateEntities),
                    maker.getGuid(),
                    maker.getName()
            );
        } finally {
            if (acquiredSyncSlot) {
                semaphore.release();
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SlicingValidationStatusResponse getValidationStatus(String orderGuid) {
        User caller = ServiceContext.getUserOrThrow();
        Order order = orderRepository.findOrderByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        requireAuthorizedReader(order, caller);
        List<OrderFileAsset> plates = orderFileAssetRepository
                .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE);
        return buildStatusResponse(order, plates);
    }

    @Override
    @Transactional
    public SlicingValidationStatusResponse confirmRecalculatedQuote(String orderGuid) {
        User buyer = ServiceContext.getUserOrThrow();
        Order order = orderRepository.findLockedByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        if (!Objects.equals(order.getBuyerId(), buyer.getId())) {
            throw new ForbiddenException();
        }
        if (order.getPricingSource() != OrderPricingSource.INSTANT_QUOTE
                || order.getSlicingValidationStatus() != SlicingValidationStatus.RECALCULATED
                || order.getServerValidatedAmount() == null
                || order.getServerValidatedAmount().signum() <= 0) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        BigDecimal officialAmount = order.getServerValidatedAmount().setScale(2, RoundingMode.HALF_UP);
        order.setSlicingValidationStatus(SlicingValidationStatus.VALIDATED);
        order.setServiceAmount(officialAmount);
        order.setOrderTotal(officialAmount);
        order = orderRepository.save(order);
        orderStatusService.move(order, OrderStatusCd.CONFIRMED);

        List<OrderFileAsset> plates = orderFileAssetRepository
                .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE);
        return buildStatusResponse(order, plates);
    }

    @Override
    @Transactional(noRollbackFor = {UnprocessableEntityException.class, BadGatewayException.class})
    public SlicingValidationStatusResponse revalidateSlice(String orderGuid) {
        User buyer = ServiceContext.getUserOrThrow();
        Order order = orderRepository.findLockedByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));
        if (!Objects.equals(order.getBuyerId(), buyer.getId())) {
            throw new ForbiddenException();
        }
        if (order.getPricingSource() != OrderPricingSource.INSTANT_QUOTE) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        if (order.getSlicingValidationStatus() == SlicingValidationStatus.VALIDATED) {
            List<OrderFileAsset> plates = orderFileAssetRepository
                    .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE);
            return buildStatusResponse(order, plates);
        }

        executeValidationFromStoredOrder(order);
        List<OrderFileAsset> plates = orderFileAssetRepository
                .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE);
        return buildStatusResponse(order, plates);
    }

    @Override
    @Transactional(noRollbackFor = {UnprocessableEntityException.class, BadGatewayException.class})
    public void processQueuedValidation(String orderGuid) {
        Order order = orderRepository.findLockedByGuid(orderGuid).orElse(null);
        if (order == null || order.getSlicingValidationStatus() == SlicingValidationStatus.VALIDATED) {
            return;
        }
        Semaphore semaphore = semaphore();
        boolean acquired = acquireBlocking(semaphore);
        try {
            executeValidationFromStoredOrder(order);
        } catch (UnprocessableEntityException ex) {
            log.warn("Queued slicing validation rejected Order [{}]: {}", orderGuid, ex.getMessage());
        } catch (BadGatewayException ex) {
            log.error("Queued slicing validation failed for Order [{}]: {}", orderGuid, ex.getMessage());
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private void executeValidationFromStoredOrder(Order order) {
        Company maker = companyRepository.findById(order.getVendorId())
                .orElseThrow(() -> new NotFoundException(MessageCd.MAKER_NOT_FOUND));
        List<OrderFileAsset> plates = orderFileAssetRepository
                .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE);
        if (plates.isEmpty()) {
            throw new BadGatewayException(MessageCd.SLICE_VALIDATION_FAILED);
        }
        Map<Integer, PackedProjectResult> packedByPlate = new java.util.HashMap<>();
        for (OrderFileAsset plate : plates) {
            FileAsset fileAsset = fileAssetRepository.findById(plate.getFileAssetId())
                    .orElseThrow(() -> new BadGatewayException(MessageCd.SLICE_VALIDATION_FAILED));
            byte[] rawBytes = objectStorageService.downloadPrivateOrderFile(fileAsset);
            PackedProjectResult packed = projectPacker.packClean3mf(
                    fileAsset.getName(),
                    rawBytes,
                    positiveOrOne(plate.getScaleX()),
                    positiveOrOne(plate.getScaleY()),
                    positiveOrOne(plate.getScaleZ()),
                    properties.getMaxTrianglesPerPlate()
            );
            packedByPlate.put(plate.getPlateNumber(), packed);
        }
        validateAndFinalizeOrder(order, plates, maker, packedByPlate);
    }

    private void validateAndFinalizeOrder(
            Order order,
            List<OrderFileAsset> plates,
            Company maker,
            Map<Integer, PackedProjectResult> packedByPlate
    ) {
        order.setSlicingValidationStatus(SlicingValidationStatus.RUNNING);
        orderRepository.save(order);

        MakerPricingProfile profile = loadMakerPricingProfile(maker);
        Map<MaterialType, BigDecimal> materialPrices = loadMakerMaterialPrices(maker.getId());
        List<Asset> fleet = assetRepository.findAllByCompanyIdAndStatus(maker.getId(), StatusCd.ACTIVE);
        Map<Long, PrinterCatalog> catalogsById = loadCatalogsForFleet(fleet);

        List<PlateMatchSpec> specs = new ArrayList<>(plates.size());
        for (OrderFileAsset plate : plates) {
            PackedProjectResult packed = packedByPlate.get(plate.getPlateNumber());
            MaterialType mat = MakerEligibilityService.parseMaterial(plate.getMaterial());
            specs.add(new PlateMatchSpec(
                    plate.getPlateNumber(),
                    mat,
                    packed.widthMm(),
                    packed.depthMm(),
                    packed.heightMm(),
                    Boolean.TRUE.equals(plate.getIsMultiColor()),
                    plate.getColorCount() != null ? plate.getColorCount() : 1
            ));
        }

        InstantQuoteMatchResult printerMatch = makerEligibilityService.matchPrintersForInstantQuote(
                maker, fleet, catalogsById, specs, materialPrices.keySet(), printerSpeedResolver
        );
        if (!printerMatch.eligible() || printerMatch.primaryPrinter() == null) {
            markOrderFailed(order, plates, "Maker deixou de atender os requisitos técnicos do pedido.");
            throw new ConflictException(MessageCd.MAKER_NOT_ELIGIBLE_FOR_QUOTE);
        }

        List<PlateComparisonInput> comparisons = new ArrayList<>(plates.size());
        List<PlateQuoteInput> serverQuoteInputs = new ArrayList<>(plates.size());
        Instant validatedAt = Instant.now();

        for (OrderFileAsset plate : plates) {
            int plateNumber = plate.getPlateNumber();
            PackedProjectResult packed = packedByPlate.get(plateNumber);
            MaterialType mat = MakerEligibilityService.parseMaterial(plate.getMaterial());

            HeadlessSliceResult sliceResult;
            Path tempDir = null;
            try {
                tempDir = Files.createTempDirectory("imprime-slice-" + order.getGuid() + "-p" + plateNumber + "-");
                Path input3mfPath = tempDir.resolve("input.3mf");
                Files.write(input3mfPath, packed.clean3mfBytes());
                Path outputDir = tempDir.resolve("out");

                ProcessParameters procParams = new ProcessParameters(
                        plate.getLayerHeightMm(),
                        plate.getInfillPercent(),
                        plate.getInfillPattern(),
                        plate.getWallCount(),
                        plate.getSupports(),
                        plate.getSupportType(),
                        plate.getAdhesionType()
                );
                ResolvedPresetPaths presets = profileResolver.prepareJobPresets(tempDir, mat, procParams);
                sliceResult = slicerRouter.slice(new HeadlessSliceRequest(
                        tempDir,
                        input3mfPath,
                        presets.machineSettingsPath(),
                        presets.processSettingsPath(),
                        presets.filamentSettingsPath(),
                        outputDir,
                        properties.getPerPlateTimeoutSeconds(),
                        properties.getMaxTrianglesPerPlate()
                ));

                Long validated3mfId = artifactStorage.storeValidated3mf(
                        order.getGuid(),
                        plateNumber,
                        order.getBuyerId(),
                        sliceResult.exported3mfPath()
                );
                plate.setValidated3mfFileAssetId(validated3mfId);
            } catch (BadGatewayException ex) {
                if (ex.getMessageCd() == MessageCd.SLICER_UNAVAILABLE && properties.getValidation().isFailOpenOnUnavailable()) {
                    log.warn("SLICER_UNAVAILABLE with failOpenOnUnavailable=true for Order [{}] plate {}", order.getGuid(), plateNumber);
                    BigDecimal modelW = nonNullZero(plate.getClientModelWeightGrams());
                    BigDecimal supW = nonNullZero(plate.getClientSupportWeightGrams());
                    sliceResult = new HeadlessSliceResult(
                            modelW,
                            supW,
                            modelW.add(supW),
                            plate.getClientBasePrintTimeSeconds() != null ? plate.getClientBasePrintTimeSeconds() : 60,
                            null,
                            null,
                            "FAIL_OPEN"
                    );
                } else {
                    markOrderFailed(order, plates, ex.getMessage());
                    throw ex;
                }
            } catch (IOException ex) {
                markOrderFailed(order, plates, "Erro de E/S ao preparar fatiamento headless.");
                throw new BadGatewayException(MessageCd.SLICE_VALIDATION_FAILED);
            } finally {
                deleteQuietly(tempDir);
            }

            plate.setDimensionXMm(packed.widthMm());
            plate.setDimensionYMm(packed.depthMm());
            plate.setDimensionZMm(packed.heightMm());
            plate.setServerModelWeightGrams(sliceResult.modelWeightGrams());
            plate.setServerSupportWeightGrams(sliceResult.supportWeightGrams());
            plate.setServerTotalWeightGrams(sliceResult.totalWeightGrams());
            plate.setServerBasePrintTimeSeconds(sliceResult.basePrintTimeSeconds());
            plate.setSlicingValidatedAt(validatedAt);
            plate.setSlicingValidationError(null);

            BigDecimal clientTotalWeight = nonNullZero(plate.getClientModelWeightGrams())
                    .add(nonNullZero(plate.getClientSupportWeightGrams()));
            int clientBaseSeconds = plate.getClientBasePrintTimeSeconds() != null
                    ? plate.getClientBasePrintTimeSeconds()
                    : 1;

            comparisons.add(new PlateComparisonInput(
                    plateNumber,
                    clientTotalWeight,
                    clientBaseSeconds,
                    sliceResult.totalWeightGrams(),
                    sliceResult.basePrintTimeSeconds()
            ));

            MatchedPlatePrinter mp = printerMatch.plateMatches().get(plateNumber);
            serverQuoteInputs.add(new PlateQuoteInput(
                    plateNumber,
                    plate.getPlateName(),
                    mat,
                    plate.getQuantity() != null ? plate.getQuantity() : 1,
                    sliceResult.modelWeightGrams(),
                    sliceResult.supportWeightGrams(),
                    sliceResult.basePrintTimeSeconds(),
                    plate.getFinish(),
                    plate.getUrgency(),
                    mp.assetId(),
                    mp.printerGuid(),
                    mp.printerName(),
                    mp.printerModel(),
                    mp.speedTier(),
                    mp.speedMultiplier()
            ));
        }

        OrderComparisonSummary summary = validationComparator.compare(properties.getValidation(), comparisons);
        CalculatedOrderQuote serverQuote = quoteCalculator.calculate(profile, materialPrices, serverQuoteInputs);
        Map<Integer, PlateComparisonResult> resultByPlate = summary.plates().stream()
                .collect(Collectors.toMap(PlateComparisonResult::plateNumber, Function.identity()));
        Map<Integer, BigDecimal> serverPriceByPlate = serverQuote.plateQuotes().stream()
                .collect(Collectors.toMap(
                        MarketplaceQuoteCalculator.CalculatedPlateQuote::plateNumber,
                        MarketplaceQuoteCalculator.CalculatedPlateQuote::totalPriceBrl
                ));

        for (OrderFileAsset plate : plates) {
            PlateComparisonResult cr = resultByPlate.get(plate.getPlateNumber());
            if (cr != null) {
                plate.setWeightDivergencePercent(cr.weightDivergencePercent());
                plate.setTimeDivergencePercent(cr.timeDivergencePercent());
                plate.setSlicingValidationStatus(cr.plateStatus());
            }
            plate.setEstimatedWeightGrams(plate.getServerTotalWeightGrams());
            if (plate.getServerBasePrintTimeSeconds() != null) {
                plate.setEstimatedPrintTimeMinutes(Math.max(1, (int) Math.ceil(plate.getServerBasePrintTimeSeconds() / 60.0d)));
            }
            if (serverPriceByPlate.containsKey(plate.getPlateNumber())) {
                plate.setEstimatedPriceBrl(serverPriceByPlate.get(plate.getPlateNumber()));
            }
        }
        orderFileAssetRepository.saveAll(plates);

        MatchedPlatePrinter primary = printerMatch.primaryPrinter();
        order.setSelectedPrinterAssetId(primary.assetId());
        order.setSelectedPrinterName(truncate(primary.printerName(), 120));
        order.setSelectedSpeedTier(primary.speedTier());
        order.setSelectedSpeedMultiplier(primary.speedMultiplier());
        order.setEstimatedPrintTimeSeconds(serverQuote.totalEstimatedPrintTimeSeconds());
        order.setProductionDays(serverQuote.estimatedLeadTimeDays());
        order.setServerValidatedAmount(serverQuote.totalPriceBrl());
        order.setSlicingValidatedAt(validatedAt);
        order.setSlicingValidationSummaryJson(jsonCodec.toJson(summary));
        order.setSlicingValidationStatus(summary.overallStatus());

        if (summary.overallStatus() == SlicingValidationStatus.VALIDATED) {
            order.setServiceAmount(serverQuote.totalPriceBrl());
            order.setOrderTotal(serverQuote.totalPriceBrl());
            orderRepository.save(order);
            orderStatusService.move(order, OrderStatusCd.CONFIRMED);
        } else if (summary.overallStatus() == SlicingValidationStatus.RECALCULATED) {
            orderRepository.save(order);
        } else {
            orderRepository.save(order);
            throw new UnprocessableEntityException(
                    String.format(
                            "Divergência detectada pelo servidor: peso %.2f%%, tempo %.2f%%. Valor oficial recalculado: R$ %s.",
                            summary.maxWeightDivergencePercent(),
                            summary.maxTimeDivergencePercent(),
                            serverQuote.totalPriceBrl()
                    ),
                    MessageCd.SLICE_DIVERGENCE_EXCEEDED
            );
        }
    }

    private void finalizeWithClientMetricsWhenValidationDisabled(
            Order order,
            List<OrderFileAsset> plates,
            CalculatedOrderQuote clientCalculated
    ) {
        Instant now = Instant.now();
        for (OrderFileAsset plate : plates) {
            BigDecimal m = nonNullZero(plate.getClientModelWeightGrams());
            BigDecimal s = nonNullZero(plate.getClientSupportWeightGrams());
            plate.setServerModelWeightGrams(m);
            plate.setServerSupportWeightGrams(s);
            plate.setServerTotalWeightGrams(m.add(s));
            plate.setServerBasePrintTimeSeconds(plate.getClientBasePrintTimeSeconds());
            plate.setWeightDivergencePercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            plate.setTimeDivergencePercent(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            plate.setSlicingValidationStatus(SlicingValidationStatus.VALIDATED);
            plate.setSlicingValidatedAt(now);
        }
        orderFileAssetRepository.saveAll(plates);

        order.setServerValidatedAmount(clientCalculated.totalPriceBrl());
        order.setServiceAmount(clientCalculated.totalPriceBrl());
        order.setOrderTotal(clientCalculated.totalPriceBrl());
        order.setSlicingValidationStatus(SlicingValidationStatus.VALIDATED);
        order.setSlicingValidatedAt(now);
        orderRepository.save(order);
        orderStatusService.move(order, OrderStatusCd.CONFIRMED);
    }

    private SlicingValidationStatusResponse buildStatusResponse(Order order, List<OrderFileAsset> plates) {
        SlicingValidationStatus status = order.getSlicingValidationStatus() != null
                ? order.getSlicingValidationStatus()
                : SlicingValidationStatus.PENDING;
        boolean checkoutReady = status == SlicingValidationStatus.VALIDATED
                && order.getServerValidatedAmount() != null
                && order.getServerValidatedAmount().signum() > 0;
        boolean requiresConfirmation = status == SlicingValidationStatus.RECALCULATED;

        BigDecimal maxWeightDiv = null;
        BigDecimal maxTimeDiv = null;
        boolean allHave3mf = !plates.isEmpty();
        List<PlateValidationDetail> details = new ArrayList<>(plates.size());

        for (OrderFileAsset plate : plates) {
            if (plate.getWeightDivergencePercent() != null
                    && (maxWeightDiv == null || plate.getWeightDivergencePercent().compareTo(maxWeightDiv) > 0)) {
                maxWeightDiv = plate.getWeightDivergencePercent();
            }
            if (plate.getTimeDivergencePercent() != null
                    && (maxTimeDiv == null || plate.getTimeDivergencePercent().compareTo(maxTimeDiv) > 0)) {
                maxTimeDiv = plate.getTimeDivergencePercent();
            }
            boolean has3mf = plate.getValidated3mfFileAssetId() != null;
            if (!has3mf) {
                allHave3mf = false;
            }
            details.add(new PlateValidationDetail(
                    plate.getPlateNumber() != null ? plate.getPlateNumber() : 1,
                    plate.getPlateName(),
                    plate.getMaterial(),
                    plate.getQuantity() != null ? plate.getQuantity() : 1,
                    plate.getClientModelWeightGrams(),
                    plate.getClientSupportWeightGrams(),
                    plate.getClientBasePrintTimeSeconds(),
                    plate.getServerModelWeightGrams(),
                    plate.getServerSupportWeightGrams(),
                    plate.getServerTotalWeightGrams(),
                    plate.getServerBasePrintTimeSeconds(),
                    plate.getWeightDivergencePercent(),
                    plate.getTimeDivergencePercent(),
                    plate.getSlicingValidationStatus() != null ? plate.getSlicingValidationStatus() : status,
                    has3mf,
                    plate.getSlicingValidationError()
            ));
        }

        String msg = switch (status) {
            case PENDING, RUNNING -> "Validação de fatiamento em processamento no servidor.";
            case VALIDATED -> "Orçamento validado pelo servidor com sucesso.";
            case RECALCULATED -> "O valor foi recalculado após o fatiamento no servidor e aguarda sua confirmação.";
            case REJECTED -> "Divergência excessiva detectada entre os dados enviados e o fatiamento no servidor.";
            case FAILED -> "Não foi possível validar uma ou mais mesas no fatiador do servidor.";
        };

        return new SlicingValidationStatusResponse(
                order.getGuid(),
                order.getOrderCode(),
                status,
                checkoutReady,
                requiresConfirmation,
                order.getClientQuotedAmount(),
                order.getServerValidatedAmount(),
                maxWeightDiv,
                maxTimeDiv,
                order.getSelectedPrinterName(),
                order.getSelectedSpeedTier(),
                order.getSelectedSpeedMultiplier(),
                order.getEstimatedPrintTimeSeconds(),
                order.getProductionDays(),
                allHave3mf,
                "/orders/" + order.getGuid() + "/slicing-validation",
                order.getSlicingValidatedAt(),
                msg,
                details
        );
    }

    private OrderFileAsset buildOrderFileAsset(
            Long orderId,
            FileAsset asset,
            PackedPlateSource ps,
            String engineId,
            String profileId,
            MatchedPlatePrinter mp
    ) {
        PlateCheckoutInput input = ps.input();
        OrderFileAsset entity = new OrderFileAsset();
        entity.setOrderId(orderId);
        entity.setFileAssetId(asset.getId());
        entity.setPlateNumber(resolvedPlateNumber(input, ps.index()));
        entity.setPlateName(truncate(resolvePlateName(input, ps.file(), ps.index()), 180));
        entity.setMaterial(truncate(ps.material().name(), 60));
        entity.setColorHex(truncate(input.colorHex(), 30));
        entity.setQuantity(input.quantity());
        entity.setScaleX(positiveOrOne(input.scaleX()));
        entity.setScaleY(positiveOrOne(input.scaleY()));
        entity.setScaleZ(positiveOrOne(input.scaleZ()));
        entity.setDimensionXMm(ps.packed().widthMm());
        entity.setDimensionYMm(ps.packed().depthMm());
        entity.setDimensionZMm(ps.packed().heightMm());
        entity.setLayerHeightMm(input.layerHeightMm());
        entity.setInfillPercent(input.infillPercent());
        entity.setInfillPattern(truncate(input.infillPattern(), 40));
        entity.setWallCount(input.wallCount());
        entity.setSupports(input.supports());
        entity.setSupportType(truncate(input.supportType(), 40));
        entity.setAdhesionType(truncate(input.adhesionType(), 40));
        entity.setFinish(truncate(input.finish(), 40));
        entity.setTolerance(truncate(input.tolerance(), 40));
        entity.setUrgency(truncate(input.urgency(), 20));
        boolean multi = Boolean.TRUE.equals(input.isMultiColor());
        int colorsCount = input.colorCount() != null && input.colorCount() > 0 ? input.colorCount() : (multi ? 2 : 1);
        entity.setIsMultiColor(multi || colorsCount > 1);
        entity.setColorCount(colorsCount);
        entity.setPlateNotes(hasText(input.notes()) ? input.notes().trim() : null);
        if (input.colors() != null && !input.colors().isEmpty()) {
            entity.setColorsJson(jsonCodec.toJsonArray(input.colors()));
            if (entity.getColorHex() == null) {
                entity.setColorHex(truncate(input.colors().getFirst(), 30));
            }
        }
        entity.setSlicerEngineId(truncate(engineId, 30));
        entity.setSlicerProfileId(truncate(profileId, 120));
        entity.setClientModelWeightGrams(scale2(input.clientModelWeightGrams()));
        entity.setClientSupportWeightGrams(scale2(input.clientSupportWeightGrams() != null ? input.clientSupportWeightGrams() : BigDecimal.ZERO));
        entity.setClientBasePrintTimeSeconds(input.clientBasePrintTimeSeconds());
        BigDecimal clientTotal = entity.getClientModelWeightGrams().add(entity.getClientSupportWeightGrams());
        entity.setEstimatedWeightGrams(clientTotal);
        entity.setEstimatedPrintTimeMinutes(Math.max(1, (int) Math.ceil((input.clientBasePrintTimeSeconds() * mp.speedMultiplier().doubleValue()) / 60.0d)));
        entity.setSlicingValidationStatus(SlicingValidationStatus.PENDING);
        return entity;
    }

    private void enqueueValidationAfterCommit(RabbitTemplate rabbitTemplate, String orderGuid, Long userId) {
        SlicingValidationQueueMessage msg = new SlicingValidationQueueMessage(orderGuid, userId, 1);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(
                            properties.getRabbitmq().getExchange(),
                            properties.getRabbitmq().getRoutingKey(),
                            msg
                    );
                }
            });
        } else {
            rabbitTemplate.convertAndSend(
                    properties.getRabbitmq().getExchange(),
                    properties.getRabbitmq().getRoutingKey(),
                    msg
            );
        }
    }

    private void markOrderFailed(Order order, List<OrderFileAsset> plates, String errorMessage) {
        String truncatedErr = truncate(errorMessage, 480);
        order.setSlicingValidationStatus(SlicingValidationStatus.FAILED);
        orderRepository.save(order);
        for (OrderFileAsset plate : plates) {
            if (plate.getSlicingValidationStatus() == SlicingValidationStatus.PENDING
                    || plate.getSlicingValidationStatus() == SlicingValidationStatus.RUNNING
                    || plate.getSlicingValidationStatus() == null) {
                plate.setSlicingValidationStatus(SlicingValidationStatus.FAILED);
                plate.setSlicingValidationError(truncatedErr);
            }
        }
        orderFileAssetRepository.saveAll(plates);
    }

    private void requireAuthorizedReader(Order order, User caller) {
        boolean isBuyer = Objects.equals(order.getBuyerId(), caller.getId());
        boolean isMaker = caller.getCompanyId() != null && Objects.equals(order.getVendorId(), caller.getCompanyId());
        if (!isBuyer && !isMaker) {
            throw new ForbiddenException();
        }
    }

    private MakerPricingProfile loadMakerPricingProfile(Company maker) {
        return pricingProfileRepository.findByCompanyIdAndStatus(maker.getId(), StatusCd.ACTIVE)
                .orElseGet(() -> {
                    MakerPricingProfile profile = MakerPricingProfile.defaultForCompany(maker.getId());
                    if (maker.getMinOrderValue() != null && maker.getMinOrderValue().signum() > 0) {
                        profile.setMinimumOrderFeeBrl(maker.getMinOrderValue());
                    }
                    if (maker.getMinLeadTimeDays() != null && maker.getMinLeadTimeDays() >= 1) {
                        profile.setLeadTimeDays(Math.min(90, maker.getMinLeadTimeDays()));
                    }
                    return profile;
                });
    }

    private Map<MaterialType, BigDecimal> loadMakerMaterialPrices(Long companyId) {
        Map<MaterialType, BigDecimal> map = new EnumMap<>(MaterialType.class);
        for (MakerMaterialPrice row : materialPriceRepository.findAllByCompanyIdAndStatusOrderByMaterialAsc(companyId, StatusCd.ACTIVE)) {
            if (Boolean.TRUE.equals(row.getEnabled()) && row.getMaterial() != null && row.getPricePerGramBrl() != null) {
                map.put(row.getMaterial(), row.getPricePerGramBrl());
            }
        }
        return map;
    }

    private Map<Long, PrinterCatalog> loadCatalogsForFleet(List<Asset> fleet) {
        List<Long> ids = fleet.stream()
                .map(Asset::getPrinterCatalogId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return printerCatalogRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(PrinterCatalog::getId, Function.identity()));
    }

    private Semaphore semaphore() {
        Semaphore existing = executionSemaphore.get();
        if (existing != null) {
            return existing;
        }
        Semaphore created = new Semaphore(Math.max(1, properties.getMaxConcurrentJobs()), true);
        if (executionSemaphore.compareAndSet(null, created)) {
            return created;
        }
        return executionSemaphore.get();
    }

    private boolean acquireBlocking(Semaphore semaphore) {
        try {
            semaphore.acquire();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadGatewayException(MessageCd.SLICER_UNAVAILABLE);
        }
    }

    private void cleanupUploadedAssets(List<FileAsset> assets) {
        for (FileAsset asset : assets) {
            try {
                fileAssetService.deleteForever(asset);
            } catch (Exception ex) {
                log.warn("Failed to cleanup FileAsset [{}]: {}", asset.getGuid(), ex.getMessage());
            }
        }
    }

    private static void deleteQuietly(@Nullable Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static int resolvedPlateNumber(PlateCheckoutInput input, int index) {
        return input.plateNumber() != null && input.plateNumber() > 0 ? input.plateNumber() : (index + 1);
    }

    private static String resolvePlateName(PlateCheckoutInput input, MultipartFile file, int index) {
        return CatalogDtoMapper.firstNonBlank(input.plateName(), file.getOriginalFilename(), "Mesa " + (index + 1));
    }

    private static @Nullable UUID parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static double positiveOrOne(@Nullable Double val) {
        return val != null && val > 0.0d && Double.isFinite(val) ? val : 1.0d;
    }

    private static boolean hasText(@Nullable String val) {
        return val != null && !val.isBlank();
    }

    private static @Nullable String truncate(@Nullable String val, int maxLen) {
        if (val == null || val.isBlank()) {
            return null;
        }
        String trimmed = val.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }

    private static BigDecimal nonNullZero(@Nullable BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private static BigDecimal scale2(BigDecimal val) {
        return val.setScale(2, RoundingMode.HALF_UP);
    }

    private record PackedPlateSource(
            int index,
            MultipartFile file,
            byte[] rawBytes,
            PlateCheckoutInput input,
            MaterialType material,
            PackedProjectResult packed
    ) {
    }
}
