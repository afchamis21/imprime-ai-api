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
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.util.CatalogDtoMapper;
import org.imprime.ai.api.util.JsonCodec;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public StartOrderResponse start(MultipartFile model, StartOrderRequest request) {
        return start(model == null ? List.of() : List.of(model), null, request);
    }

    public StartOrderResponse start(
            List<MultipartFile> models,
            @Nullable String platesMetadataJson,
            StartOrderRequest request) {
        if (models == null || models.isEmpty()) {
            throw new BadRequestException(MessageCd.FILE_EMPTY);
        }

        // 1. Validate the Address Exists and belongs to the user
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

        // 2-4. Validate & upload all model files, rolling back earlier uploads if any fails
        List<FileAsset> uploadedAssets = new ArrayList<>(models.size());
        try {
            for (MultipartFile model : models) {
                uploadedAssets.add(fileAssetService.upload(model, FileAsset.AssetType.MODEL));
            }
        } catch (RuntimeException e) {
            cleanupUploadedAssets(uploadedAssets);
            throw e;
        }

        List<OrderPlateMetadataRequest> platesMetadata = (platesMetadataJson != null && !platesMetadataJson.isBlank())
                ? jsonCodec.readList(platesMetadataJson, OrderPlateMetadataRequest.class)
                : List.of();

        // 5. Create the Order and its OrderFileAsset plate records
        Order order = new Order();
        try {
            order.setToAddressId(address.getId());
            order.setBuyerId(caller.getId());
            order.setFileAssetId(uploadedAssets.getFirst().getId());
            order = orderRepository.save(order);

            List<OrderFileAsset> plateEntities = new ArrayList<>(uploadedAssets.size());
            for (int i = 0; i < uploadedAssets.size(); i++) {
                FileAsset asset = uploadedAssets.get(i);
                OrderPlateMetadataRequest meta = i < platesMetadata.size() ? platesMetadata.get(i) : null;
                plateEntities.add(buildOrderFileAsset(order.getId(), asset, meta, i));
            }
            orderFileAssetRepository.saveAll(plateEntities);
        } catch (Exception e) {
            log.error("Error persisting order! Rolling back File Assets", e);
            cleanupUploadedAssets(uploadedAssets);
            throw new InternalErrorException();
        }

        // 5.2 Create the Created Status
        orderStatusService.move(order, OrderStatusCd.CREATED);
        // 5.3 Create the Quoting Status
        orderStatusService.move(order, OrderStatusCd.QUOTING);

        // 6-7. Fetch list of makers
        try {
            List<CompanyChoiceDTO> companyChoiceDTOS = companyService.findCompaniesForAddress(address, 100);
            return new StartOrderResponse(order.getGuid(), AddressDTO.from(address), companyChoiceDTOS);
        } catch (Exception e) {
            log.error("Error searching companies for order. Returning Order Guid to the UI", e);
            ServiceContext.getContext().addMessage(MessageCd.ERROR_LOADING_MAKERS);
            return new StartOrderResponse(order.getGuid(), AddressDTO.from(address), List.of());
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
        entity.setIsMultiColor(Boolean.TRUE.equals(meta != null ? meta.isMultiColor() : null));
        entity.setPaintingInstructions(
                meta != null && meta.paintingInstructions() != null && !meta.paintingInstructions().isBlank()
                        ? meta.paintingInstructions().trim()
                        : null);
        return entity;
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

        Optional<Address> optionalAddress = addressService.findById(optionalOrder.get().getToAddressId());
        if (optionalAddress.isEmpty()) {
            throw new BadRequestException(MessageCd.INTERNAL_SERVER_ERROR); // TODO
        }

        return companyService.findCompaniesForAddress(optionalAddress.get(), max);
    }

    public Optional<Order> findByGuid(String guid) {
        return orderRepository.findOrderByGuid(guid);
    }

    @Transactional
    public OrderStatus acceptOffer(String orderGuid, BigDecimal offer, Company company) {
        Order order = findByGuid(orderGuid).orElseThrow();
        order.setOrderTotal(offer);
        order.setVendorId(company.getId());

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
                copy.setIsMultiColor(src.getIsMultiColor());
                copy.setPaintingInstructions(src.getPaintingInstructions());
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
