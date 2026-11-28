package org.imprime.ai.api.service;

import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.OrderPlateMetadataRequest;
import org.imprime.ai.api.http.request.order.StartOrderRequest;
import org.imprime.ai.api.http.response.StartOrderResponse;
import org.imprime.ai.api.model.*;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.CompanyChoiceDTO;
import org.imprime.ai.api.model.enums.EntityType;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.imprime.ai.api.validator.PrintFileValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final AddressService addressService;
    private final CompanyService companyService;
    private final FileAssetService fileAssetService;
    private final OrderRepository orderRepository;
    private final OrderFileAssetRepository orderFileAssetRepository;
    private final OrderStatusService orderStatusService;
    private final JsonCodec jsonCodec;
    private final LegalDocumentService legalDocuments;
    private final PrintFileValidator printFileValidator;
    private final MakerOrderComplianceService makerCompliance;
    private final ChatMessageRepository chatMessages;
    private final OrderUploadRequestService uploadRequests;

    @Transactional
    public StartOrderResponse start(MultipartFile model, StartOrderRequest request) {
        return start(model == null ? List.of() : List.of(model), null, request);
    }

    @Transactional
    public StartOrderResponse start(
            List<MultipartFile> models,
            @Nullable String platesMetadataJson,
            StartOrderRequest request) {
        return start(models, platesMetadataJson, request, null);
    }

    @Transactional
    public StartOrderResponse start(List<MultipartFile> models, @Nullable String platesMetadataJson,
                                    StartOrderRequest request, @Nullable UUID requestId) {
        if (models == null || models.isEmpty()) {
            throw new BadRequestException(MessageCd.FILE_EMPTY);
        }

        if (requestId == null) return createQuote(models, platesMetadataJson, request);

        long buyerId = ServiceContext.getUserOrThrow().getId();
        String existingGuid = uploadRequests.reserveOrReplay(buyerId, requestId, "START");
        if (existingGuid != null) {
            Order existing = orderRepository.findOrderByGuid(existingGuid)
                    .orElseThrow(() -> new ConflictException(MessageCd.CONFLICT));
            Address address = addressService.findById(existing.getToAddressId())
                    .orElseThrow(() -> new BadRequestException(MessageCd.INVALID_ADDRESS_GUID));
            return new StartOrderResponse(existingGuid, AddressDTO.from(address), getMakerOptions(existingGuid, 100));
        }
        StartOrderResponse response = createQuote(models, platesMetadataJson, request);
        uploadRequests.finish(buyerId, requestId, response.orderGuid());
        return response;
    }

    @Transactional
    public StartOrderResponse startQuoteDraft(String addressGuid) {
        return createQuote(List.of(), null, new StartOrderRequest(addressGuid, null, null, null));
    }

    private StartOrderResponse createQuote(List<MultipartFile> models, String platesMetadataJson,
                                           StartOrderRequest request) {
        // Validate the address before storing files.
        Optional<Address> optionalAddress = addressService.findByGuid(request.addressGuid());
        if (optionalAddress.isEmpty()) {
            log.warn("No address found for given guid: {}", request.addressGuid());
            throw new BadRequestException(MessageCd.INVALID_ADDRESS_GUID);
        }

        User caller = ServiceContext.getUserOrThrow();
        Address address = optionalAddress.get();

        if (!EntityType.USER.equals(address.getOwnerType()) || !caller.getId().equals(address.getOwnerId())) {
            log.warn("Address [{}. Owner {} | {}] does not belong to user [{}]", address.getId(), address.getOwnerId(), address.getOwnerType(), caller.getId());
            throw new ForbiddenException();
        }

        if (!StatusCd.ACTIVE.equals(address.getStatus())) {
            log.warn("Address [{}] is not Active [{}]", address.getId(), address.getStatus());
            throw new BadRequestException(MessageCd.ADDRESS_NOT_ACTIVE);
        }

        List<OrderPlateMetadataRequest> platesMetadata = (platesMetadataJson != null && !platesMetadataJson.isBlank())
                ? jsonCodec.readList(platesMetadataJson, OrderPlateMetadataRequest.class)
                : List.of();
        Order order = new Order();
        order.setToAddressId(address.getId());
        order.setBuyerId(caller.getId());
        List<OrderFileAsset> plateEntities = storeQuoteFiles(order, models, platesMetadata, request, 0);

        // 5.2 Create the Created Status
        orderStatusService.move(order, OrderStatusCd.CREATED);
        // 5.3 Create the Quoting Status
        orderStatusService.move(order, OrderStatusCd.QUOTING);

        // 6-7. Fetch list of eligible makers for order requirements
        try {
            List<CompanyChoiceDTO> companyChoiceDTOS = companyService.findCompaniesForOrder(address, plateEntities, 100);
            return new StartOrderResponse(order.getGuid(), AddressDTO.from(address), companyChoiceDTOS);
        } catch (Exception e) {
            log.error("Error searching companies for order. Returning Order Guid to the UI", e);
            ServiceContext.getContext().addMessage(MessageCd.ERROR_LOADING_MAKERS);
            return new StartOrderResponse(order.getGuid(), AddressDTO.from(address), List.of());
        }
    }

    @Transactional
    public void attachQuoteFiles(String orderGuid, List<MultipartFile> files, StartOrderRequest declaration,
                                 @Nullable UUID requestId) {
        if (files == null || files.isEmpty()) throw new BadRequestException(MessageCd.FILE_EMPTY);
        Order order = makerCompliance.lockOrder(orderGuid);
        if (!Objects.equals(order.getBuyerId(), ServiceContext.getUserOrThrow().getId())) {
            throw new ForbiddenException();
        }
        if (requestId != null
                && uploadRequests.reserveOrReplay(order.getBuyerId(), requestId, "ATTACH:" + orderGuid) != null) return;
        if (order.getVendorId() != null || orderStatusService.findLatest(order).getStatusCd() != OrderStatusCd.QUOTING) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        if (chatMessages.existsOfferForOrder(orderGuid, MessageType.OFFER)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }
        List<OrderFileAsset> plates = orderFileAssetRepository
                .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE);
        if (order.getFileAssetId() != null && plates.isEmpty()) {
            // Keep the original legacy plate discoverable in the multi-plate list.
            FileAsset legacyAsset = new FileAsset();
            legacyAsset.setId(order.getFileAssetId());
            plates = List.of(buildOrderFileAsset(order.getId(), legacyAsset, null, 0));
            orderFileAssetRepository.saveAll(plates);
        }
        if (files.size() + plates.size() > PrintFileValidator.MAX_PLATES) {
            throw new BadRequestException(MessageCd.PRINT_FILE_INVALID);
        }
        int lastPlate = plates.stream().mapToInt(plate -> plate.getPlateNumber()).max().orElse(0);
        storeQuoteFiles(order, files, List.of(), declaration, lastPlate);
        if (requestId != null) uploadRequests.finish(order.getBuyerId(), requestId, orderGuid);
    }

    private List<OrderFileAsset> storeQuoteFiles(Order order, List<MultipartFile> models,
                                                  List<OrderPlateMetadataRequest> platesMetadata,
                                                  StartOrderRequest request, int priorPlates) {
        Long declarationId = null;
        Long prohibitedItemsId = null;
        List<String> fileHashes = List.of();
        if (!models.isEmpty()) {
            if (!Boolean.TRUE.equals(request.rightsDeclared())) {
                throw new BadRequestException(MessageCd.PRINT_DECLARATION_REQUIRED);
            }
            declarationId = legalDocuments.requireAcceptableVersion("print-declaration", request.declarationVersion());
            prohibitedItemsId = legalDocuments.requireAcceptableVersion("prohibited-items", request.prohibitedItemsVersion());
            printFileValidator.validate(models);
            fileHashes = models.stream().map(this::fileDigest).toList();
        }

        // Register object cleanup for any rollback, including a failure at commit time.
        List<FileAsset> uploadedAssets = new ArrayList<>(models.size());
        boolean transactional = TransactionSynchronizationManager.isSynchronizationActive();
        if (transactional) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        uploadedAssets.forEach(fileAssetService::deleteUploadedObject);
                    }
                }
            });
        }
        try {
            for (MultipartFile model : models) {
                uploadedAssets.add(fileAssetService.uploadOrderModel(model));
            }
        } catch (RuntimeException e) {
            if (!transactional) cleanupUploadedAssets(uploadedAssets);
            throw e;
        }

        // 5. Create the Order and its OrderFileAsset plate records
        List<OrderFileAsset> plateEntities = new ArrayList<>(uploadedAssets.size());
        try {
            if (!uploadedAssets.isEmpty() && order.getFileAssetId() == null) {
                order.setFileAssetId(uploadedAssets.getFirst().getId());
            }
            Order saved = orderRepository.save(order);

            for (int i = 0; i < uploadedAssets.size(); i++) {
                FileAsset asset = uploadedAssets.get(i);
                OrderPlateMetadataRequest meta = i < platesMetadata.size() ? platesMetadata.get(i) : null;
                plateEntities.add(buildOrderFileAsset(saved.getId(), asset, meta, priorPlates + i));
                legalDocuments.recordOrderFileDeclaration(saved.getId(), asset.getId(), order.getBuyerId(),
                        declarationId, prohibitedItemsId, fileHashes.get(i));
            }
            orderFileAssetRepository.saveAll(plateEntities);
        } catch (Exception e) {
            log.error("Error persisting order! Rolling back File Assets", e);
            if (!transactional) cleanupUploadedAssets(uploadedAssets);
            throw new InternalErrorException();
        }

        return plateEntities;
    }

    private String fileDigest(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = new DigestInputStream(file.getInputStream(), digest)) {
                stream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new InternalErrorException();
        }
    }

    private OrderFileAsset buildOrderFileAsset(
            Long orderId,
            FileAsset asset,
            @Nullable OrderPlateMetadataRequest meta,
            int index) {
        OrderFileAsset entity = new OrderFileAsset();
        entity.setOrderId(orderId);
        entity.setFileAssetId(asset.getId());
        entity.setPlateNumber(meta != null && meta.plateNumber() != null && meta.plateNumber() > 0
                ? meta.plateNumber()
                : index + 1);
        entity.setPlateName(truncate(
                CatalogDtoMapper.firstNonBlank(
                        meta != null ? meta.plateName() : null,
                        asset.getName(),
                        "Mesa " + (index + 1)),
                180));
        entity.setMaterial(truncate(meta != null ? meta.material() : null, 60));
        entity.setColorHex(truncate(meta != null ? meta.colorHex() : null, 30));
        entity.setInfillPercent(meta != null ? meta.infillPercent() : null);
        entity.setSupports(meta != null ? meta.supports() : null);
        entity.setScaleX(positiveOrDefault(meta != null ? meta.scaleX() : null, 1.0));
        entity.setScaleY(positiveOrDefault(meta != null ? meta.scaleY() : null, 1.0));
        entity.setScaleZ(positiveOrDefault(meta != null ? meta.scaleZ() : null, 1.0));
        entity.setDimensionXMm(positiveBigDecimalOrNull(meta != null ? meta.dimensionXMm() : null));
        entity.setDimensionYMm(positiveBigDecimalOrNull(meta != null ? meta.dimensionYMm() : null));
        entity.setDimensionZMm(positiveBigDecimalOrNull(meta != null ? meta.dimensionZMm() : null));

        boolean explicitMulti = Boolean.TRUE.equals(meta != null ? meta.isMultiColor() : null);
        int resolvedColors = meta != null && meta.colorCount() != null && meta.colorCount() > 0
                ? meta.colorCount()
                : (explicitMulti ? 2 : 1);
        entity.setColorCount(resolvedColors);
        entity.setIsMultiColor(explicitMulti || resolvedColors > 1);
        entity.setLayerHeightMm(positiveBigDecimalOrNull(meta != null ? meta.layerHeightMm() : null));
        entity.setUrgency(truncate(meta != null ? meta.urgency() : null, 20));
        entity.setPaintingInstructions(
                meta != null && meta.paintingInstructions() != null && !meta.paintingInstructions().isBlank()
                        ? meta.paintingInstructions().trim()
                        : null);
        entity.setQuantity(meta != null && meta.quantity() != null && meta.quantity() > 0 ? meta.quantity() : 1);
        entity.setWallCount(meta != null && meta.wallCount() != null && meta.wallCount() > 0 ? meta.wallCount() : null);
        entity.setInfillPattern(truncate(meta != null ? meta.infillPattern() : null, 40));
        entity.setAdhesionType(truncate(meta != null ? meta.adhesionType() : null, 40));
        entity.setSupportType(truncate(meta != null ? meta.supportType() : null, 40));
        entity.setFinish(truncate(meta != null ? meta.finish() : null, 40));
        entity.setTolerance(truncate(meta != null ? meta.tolerance() : null, 40));
        entity.setPlateNotes(
                meta != null && meta.notes() != null && !meta.notes().isBlank()
                        ? meta.notes().trim()
                        : null);
        if (meta != null && meta.colors() != null && !meta.colors().isEmpty()) {
            List<String> cleanedColors = meta.colors().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(c -> !c.isEmpty())
                    .toList();
            if (!cleanedColors.isEmpty()) {
                entity.setColorsJson(jsonCodec.toJsonArray(cleanedColors));
                if (entity.getColorHex() == null) {
                    entity.setColorHex(truncate(cleanedColors.getFirst(), 30));
                }
            }
        }
        entity.setEstimatedWeightGrams(positiveBigDecimalOrNull(meta != null ? meta.estimatedWeightGrams() : null));
        entity.setEstimatedPrintTimeMinutes(
                meta != null && meta.estimatedPrintTimeMinutes() != null && meta.estimatedPrintTimeMinutes() > 0
                        ? meta.estimatedPrintTimeMinutes()
                        : null);
        entity.setEstimatedPriceBrl(positiveBigDecimalOrNull(meta != null ? meta.estimatedPriceBrl() : null));
        entity.setSlicerEngineId(truncate(meta != null ? meta.slicerEngineId() : null, 30));
        entity.setSlicerProfileId(truncate(meta != null ? meta.slicerProfileId() : null, 120));
        entity.setClientModelWeightGrams(positiveBigDecimalOrNull(meta != null ? meta.clientModelWeightGrams() : null));
        entity.setClientSupportWeightGrams(
                meta != null && meta.clientSupportWeightGrams() != null && meta.clientSupportWeightGrams().signum() >= 0
                        ? meta.clientSupportWeightGrams()
                        : null);
        entity.setClientBasePrintTimeSeconds(
                meta != null && meta.clientBasePrintTimeSeconds() != null && meta.clientBasePrintTimeSeconds() > 0
                        ? meta.clientBasePrintTimeSeconds()
                        : null);
        return entity;
    }

    private static @Nullable BigDecimal positiveBigDecimalOrNull(@Nullable BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    private void cleanupUploadedAssets(List<FileAsset> assets) {
        for (FileAsset asset : assets) {
            try {
                fileAssetService.deleteForever(asset);
            } catch (Exception cleanupEx) {
                log.warn("Failed to roll back uploaded FileAsset [{}]: {}", asset.getGuid(), cleanupEx.getMessage());
            }
        }
    }

    private static double positiveOrDefault(@Nullable Double value, double fallback) {
        return value != null && Double.isFinite(value) && value > 0 ? value : fallback;
    }

    private static @Nullable String truncate(@Nullable String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    public List<CompanyChoiceDTO> getMakerOptions(String orderGuid, Integer max) {
        Optional<Order> optionalOrder = orderRepository.findOrderByGuid(orderGuid);
        if (optionalOrder.isEmpty()) {
            throw new BadRequestException(MessageCd.INTERNAL_SERVER_ERROR); // TODO
        }

        Order order = optionalOrder.get();
        if (!Objects.equals(order.getBuyerId(), ServiceContext.getUserOrThrow().getId())) {
            throw new ForbiddenException();
        }
        Optional<Address> optionalAddress = addressService.findById(order.getToAddressId());
        if (optionalAddress.isEmpty()) {
            throw new BadRequestException(MessageCd.INTERNAL_SERVER_ERROR); // TODO
        }

        List<OrderFileAsset> plates = orderFileAssetRepository
                .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE);
        return companyService.findCompaniesForOrder(optionalAddress.get(), plates, max);
    }

    public void requireDeclarationsForExistingFiles(Order order, User buyer) {
        legalDocuments.requireDeclarationsForExistingFiles(order.getId(), buyer.getId());
    }

    public Optional<Order> findByGuid(String guid) {
        return orderRepository.findOrderByGuid(guid);
    }

    @Transactional
    public OrderStatus acceptOffer(String orderGuid, BigDecimal offer, Company company) {
        return acceptOffer(orderGuid, offer, company, null, null, null);
    }

    @Transactional
    public OrderStatus acceptOffer(
            String orderGuid,
            BigDecimal serviceOffer,
            Company company,
            @Nullable ChatMessage offerMessage,
            @Nullable OrderShippingQuote selectedQuote,
            @Nullable Long fromAddressId
    ) {
        Order order = makerCompliance.lockOrder(orderGuid);
        makerCompliance.requireNoRefusal(order.getId(), company.getId());

        BigDecimal normalizedService = serviceOffer.setScale(2, RoundingMode.HALF_UP);
        order.setVendorId(company.getId());
        order.setServiceAmount(normalizedService);
        if (fromAddressId != null) {
            order.setFromAddressId(fromAddressId);
        }
        if (offerMessage != null) {
            order.setProductionDays(offerMessage.getProductionDays());
            order.setPackageWeight(offerMessage.getPackageWeight());
            order.setPackageHeight(offerMessage.getPackageHeight());
            order.setPackageWidth(offerMessage.getPackageWidth());
            order.setPackageLength(offerMessage.getPackageLength());
        }
        if (selectedQuote != null) {
            BigDecimal shippingPrice = selectedQuote.getQuotePrice() != null
                    ? selectedQuote.getQuotePrice().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            order.setToAddressId(selectedQuote.getDeliveryAddressId());
            order.setShippingCost(shippingPrice);
            order.setShippingServiceId(selectedQuote.getServiceId());
            order.setShippingServiceName(selectedQuote.getServiceName());
            order.setShippingQuotePrice(shippingPrice);
            order.setShippingDeliveryDays(selectedQuote.getDeliveryDays());
            order.setShippingQuoteSnapshotJson(selectedQuote.getSnapshotJson());
            order.setCarrierName(selectedQuote.getCarrierName());
            order.setOrderTotal(normalizedService.add(shippingPrice).setScale(2, RoundingMode.HALF_UP));
        } else {
            order.setShippingCost(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            order.setOrderTotal(normalizedService);
        }

        orderRepository.save(order);

        return orderStatusService.move(order, OrderStatusCd.CONFIRMED);
    }

    @Transactional
    public Order cloneOrderForNewQuote(Order templateOrder, User buyer) {
        Order cloned = new Order();
        cloned.setBuyerId(buyer.getId());
        cloned.setToAddressId(templateOrder.getToAddressId());
        cloned.setFileAssetId(templateOrder.getFileAssetId());
        cloned = orderRepository.save(cloned);
        legalDocuments.copyOrderFileDeclarations(templateOrder.getId(), cloned.getId(), buyer.getId());

        List<OrderFileAsset> sourceFiles = orderFileAssetRepository
                .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(templateOrder.getId(), StatusCd.ACTIVE);
        if (!sourceFiles.isEmpty()) {
            List<OrderFileAsset> clonedFiles = new ArrayList<>(sourceFiles.size());
            for (OrderFileAsset src : sourceFiles) {
                OrderFileAsset copy = new OrderFileAsset();
                copy.setOrderId(cloned.getId());
                copy.setFileAssetId(src.getFileAssetId());
                copy.setPlateNumber(src.getPlateNumber());
                copy.setPlateName(src.getPlateName());
                copy.setMaterial(src.getMaterial());
                copy.setColorHex(src.getColorHex());
                copy.setInfillPercent(src.getInfillPercent());
                copy.setSupports(src.getSupports());
                copy.setScaleX(src.getScaleX());
                copy.setScaleY(src.getScaleY());
                copy.setScaleZ(src.getScaleZ());
                copy.setDimensionXMm(src.getDimensionXMm());
                copy.setDimensionYMm(src.getDimensionYMm());
                copy.setDimensionZMm(src.getDimensionZMm());
                copy.setColorCount(src.getColorCount());
                copy.setLayerHeightMm(src.getLayerHeightMm());
                copy.setUrgency(src.getUrgency());
                copy.setIsMultiColor(src.getIsMultiColor());
                copy.setPaintingInstructions(src.getPaintingInstructions());
                copy.setQuantity(src.getQuantity());
                copy.setWallCount(src.getWallCount());
                copy.setInfillPattern(src.getInfillPattern());
                copy.setAdhesionType(src.getAdhesionType());
                copy.setSupportType(src.getSupportType());
                copy.setFinish(src.getFinish());
                copy.setTolerance(src.getTolerance());
                copy.setPlateNotes(src.getPlateNotes());
                copy.setColorsJson(src.getColorsJson());
                copy.setEstimatedWeightGrams(src.getEstimatedWeightGrams());
                copy.setEstimatedPrintTimeMinutes(src.getEstimatedPrintTimeMinutes());
                copy.setEstimatedPriceBrl(src.getEstimatedPriceBrl());
                copy.setSlicerEngineId(src.getSlicerEngineId());
                copy.setSlicerProfileId(src.getSlicerProfileId());
                copy.setClientModelWeightGrams(src.getClientModelWeightGrams());
                copy.setClientSupportWeightGrams(src.getClientSupportWeightGrams());
                copy.setClientBasePrintTimeSeconds(src.getClientBasePrintTimeSeconds());
                clonedFiles.add(copy);
            }
            orderFileAssetRepository.saveAll(clonedFiles);
        }

        orderStatusService.move(cloned, OrderStatusCd.CREATED);
        orderStatusService.move(cloned, OrderStatusCd.QUOTING);
        return cloned;
    }

    // Fetch Order Snapshot

    // Fetch Order Chats -> might exist on chat service
    //  This will be split -> If order is in QUOTING status return active chats + potential makers
    //  If on any other status, just return the one Active chat

    // Fetch Maker List -> might exist on chat service
}
