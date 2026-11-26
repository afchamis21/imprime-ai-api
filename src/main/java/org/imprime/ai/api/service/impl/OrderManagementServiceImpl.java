package org.imprime.ai.api.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.order.UpdateOrderStatusRequest;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.http.response.order.OrderFileResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ConflictException;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.NotFoundException;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.ObjectStorageService;
import org.imprime.ai.api.service.MakerOrderSupport;
import org.imprime.ai.api.service.MakerOrderComplianceService;
import org.imprime.ai.api.service.OrderManagementService;
import org.imprime.ai.api.service.OrderStatusService;
import org.imprime.ai.api.service.OrderViewAssembler;
import org.imprime.ai.api.util.JsonCodec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManagementServiceImpl implements OrderManagementService {
    private final OrderRepository orderRepository;
    private final OrderStatusService orderStatusService;
    private final ChatRepository chatRepository;
    private final CompanyRepository companyRepository;
    private final OrderViewAssembler orderViewAssembler;
    private final OrderFileAssetRepository orderFileAssetRepository;
    private final FileAssetRepository fileAssetRepository;
    private final ObjectStorageService objectStorageService;
    private final MakerOrderSupport makerOrderSupport;
    private final JsonCodec jsonCodec;
    private final MakerOrderComplianceService makerCompliance;

    @Override
    public OrderDetailResponse getOrderDetail(String orderGuid) {
        Order order = requireAuthorizedOrder(orderGuid);
        return orderViewAssembler.toDetailResponse(order);
    }

    @Override
    public List<OrderFileResponse> listOrderFiles(String orderGuid) {
        Order order = requireAuthorizedOrder(orderGuid);
        List<OrderFileAsset> plateAssets = orderFileAssetRepository
                .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE);

        if (!plateAssets.isEmpty()) {
            Set<Long> fileIds = plateAssets.stream()
                    .map(OrderFileAsset::getFileAssetId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, FileAsset> filesById = fileIds.isEmpty()
                    ? Map.of()
                    : fileAssetRepository.findAllByIdIn(fileIds).stream()
                    .collect(Collectors.toMap(FileAsset::getId, f -> f, (a, b) -> a));

            List<OrderFileResponse> responses = new ArrayList<>(plateAssets.size());
            int fallbackIndex = 1;
            for (OrderFileAsset plate : plateAssets) {
                FileAsset asset = filesById.get(plate.getFileAssetId());
                if (asset == null) {
                    continue;
                }
                List<String> colors = resolvePlateColors(plate);
                responses.add(new OrderFileResponse(
                        asset.getGuid(),
                        asset.getName(),
                        plate.getPlateNumber() != null ? plate.getPlateNumber() : fallbackIndex,
                        plate.getPlateName() != null && !plate.getPlateName().isBlank()
                                ? plate.getPlateName()
                                : asset.getName(),
                        plate.getMaterial(),
                        plate.getColorHex(),
                        plate.getInfillPercent(),
                        plate.getSupports(),
                        plate.getScaleX() != null ? plate.getScaleX() : 1.0,
                        plate.getScaleY() != null ? plate.getScaleY() : 1.0,
                        plate.getScaleZ() != null ? plate.getScaleZ() : 1.0,
                        Boolean.TRUE.equals(plate.getIsMultiColor()),
                        plate.getPaintingInstructions(),
                        plate.getDimensionXMm(),
                        plate.getDimensionYMm(),
                        plate.getDimensionZMm(),
                        plate.getColorCount(),
                        plate.getLayerHeightMm(),
                        plate.getUrgency(),
                        plate.getQuantity() != null && plate.getQuantity() > 0 ? plate.getQuantity() : 1,
                        plate.getWallCount(),
                        plate.getInfillPattern(),
                        plate.getAdhesionType(),
                        plate.getSupportType(),
                        plate.getFinish(),
                        plate.getTolerance(),
                        plate.getPlateNotes(),
                        colors,
                        plate.getEstimatedWeightGrams(),
                        plate.getEstimatedPrintTimeMinutes(),
                        plate.getEstimatedPriceBrl()
                ));
                fallbackIndex++;
            }
            return responses;
        }

        if (order.getFileAssetId() == null) {
            return listMarketplaceModelFiles(order);
        }

        return fileAssetRepository.findById(order.getFileAssetId())
                .map(asset -> List.of(new OrderFileResponse(
                        asset.getGuid(),
                        asset.getName(),
                        1,
                        asset.getName() != null && !asset.getName().isBlank() ? asset.getName() : "Mesa 1",
                        null,
                        null,
                        null,
                        null,
                        1.0,
                        1.0,
                        1.0,
                        false,
                        null
                )))
                .orElseGet(List::of);
    }

    private List<String> resolvePlateColors(OrderFileAsset plate) {
        if (jsonCodec != null && plate.getColorsJson() != null && !plate.getColorsJson().isBlank()) {
            List<String> parsed = jsonCodec.readStringList(plate.getColorsJson());
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        if (plate.getColorHex() != null && !plate.getColorHex().isBlank()) {
            return List.of(plate.getColorHex());
        }
        return List.of();
    }

    @Override
    public OrderFileContent downloadOrderFile(String orderGuid, String fileGuid) {
        Order order = requireAuthorizedOrder(orderGuid);
        User caller = ServiceContext.getUserOrThrow();
        if (!Objects.equals(order.getBuyerId(), caller.getId())
                && (caller.getCompanyId() == null || !Objects.equals(order.getVendorId(), caller.getCompanyId()))) {
            throw new ForbiddenException();
        }
        if (!Objects.equals(order.getBuyerId(), caller.getId())) {
            companyRepository.findById(caller.getCompanyId())
                    .filter(company -> StatusCd.ACTIVE.equals(company.getStatus()))
                    .orElseThrow(ForbiddenException::new);
            makerCompliance.requireNoRefusal(order.getId(), caller.getCompanyId());
        }
        FileAsset asset = fileAssetRepository.findByGuid(fileGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND));

        boolean belongsToLegacyOrder = Objects.equals(order.getFileAssetId(), asset.getId());
        boolean belongsToOrderPlate = !belongsToLegacyOrder && orderFileAssetRepository
                .findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(order.getId(), StatusCd.ACTIVE)
                .stream()
                .anyMatch(ofa -> Objects.equals(ofa.getFileAssetId(), asset.getId()));

        if (!belongsToLegacyOrder && !belongsToOrderPlate && !belongsToMarketplaceModel(order, asset)) {
            throw new NotFoundException(MessageCd.ARCHIVE_FILE_NOT_FOUND);
        }

        byte[] bytes = belongsToLegacyOrder || belongsToOrderPlate
                ? objectStorageService.downloadPrivateOrderFile(asset)
                : objectStorageService.downloadBytes(asset);
        makerCompliance.auditFileAccess(order.getId(), asset.getId(), caller.getId());
        String fileName = asset.getName() != null && !asset.getName().isBlank()
                ? asset.getName()
                : "modelo.stl";
        String contentType = resolveContentType(fileName);

        return new OrderFileContent(fileName, contentType, bytes);
    }

    private List<MakerOrderSupport.ResolvedOrderFile> marketplaceModelFiles(Order order) {
        if (makerOrderSupport == null) {
            return List.of();
        }
        Map<Long, List<MakerOrderSupport.ResolvedOrderFile>> resolved = makerOrderSupport.resolveFiles(List.of(order));
        List<MakerOrderSupport.ResolvedOrderFile> files = resolved != null ? resolved.get(order.getId()) : null;
        if (files == null) {
            return List.of();
        }
        return files.stream().filter(f -> "MODEL_3D".equals(f.sourceType())).toList();
    }

    private List<OrderFileResponse> listMarketplaceModelFiles(Order order) {
        return marketplaceModelFiles(order).stream()
                .map(f -> new OrderFileResponse(
                        f.asset().getGuid(),
                        f.asset().getName(),
                        f.plateNumber(),
                        f.plateName() != null ? f.plateName() : f.asset().getName(),
                        f.material(),
                        f.colorHex(),
                        null,
                        null,
                        1.0,
                        1.0,
                        1.0,
                        false,
                        null
                ))
                .toList();
    }

    private boolean belongsToMarketplaceModel(Order order, FileAsset asset) {
        return marketplaceModelFiles(order).stream()
                .anyMatch(f -> Objects.equals(f.asset().getId(), asset.getId()));
    }

    private Order requireAuthorizedOrder(String orderGuid) {
        User caller = ServiceContext.getUserOrThrow();
        Order order = orderRepository.findOrderByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));

        boolean isBuyer = caller.getId().equals(order.getBuyerId());
        boolean isVendor = caller.getCompanyId() != null && caller.getCompanyId().equals(order.getVendorId());
        boolean isQuotingChatMaker = !isBuyer && !isVendor && isMakerParticipatingInQuotingChat(caller, order);

        if (!isBuyer && !isVendor && !isQuotingChatMaker) {
            log.warn("User [{}] attempted unauthorized read on Order [{}]", caller.getGuid(), orderGuid);
            throw new ForbiddenException();
        }

        return order;
    }

    private String resolveContentType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".stl")) {
            return "model/stl";
        }
        if (lower.endsWith(".3mf")) {
            return "model/3mf";
        }
        if (lower.endsWith(".glb")) {
            return "model/gltf-binary";
        }
        return "application/octet-stream";
    }

    @Override
    @Transactional
    public OrderDetailResponse updateOrderStatus(String orderGuid, UpdateOrderStatusRequest request) {
        User caller = ServiceContext.getUserOrThrow();
        Order order = orderRepository.findLockedByGuid(orderGuid)
                .orElseThrow(() -> new NotFoundException(MessageCd.ORDER_NOT_FOUND));

        boolean isBuyer = caller.getId().equals(order.getBuyerId());
        boolean isMaker = caller.getCompanyId() != null && caller.getCompanyId().equals(order.getVendorId());

        if (!isBuyer && !isMaker) {
            log.warn("User [{}] attempted unauthorized status transition on Order [{}]", caller.getGuid(), orderGuid);
            throw new ForbiddenException();
        }

        OrderStatusCd current = orderStatusService.findLatest(order).getStatusCd();
        OrderStatusCd target = request.status();

        if (!orderStatusService.canMoveTo(current, target)) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        boolean permittedForRole = (isBuyer && isBuyerTransitionAllowed(current, target))
                || (isMaker && isMakerTransitionAllowed(current, target));
        if (!permittedForRole) {
            log.warn("User [{}] (buyer={}, maker={}) is not allowed to move Order [{}] from {} to {}",
                    caller.getGuid(), isBuyer, isMaker, orderGuid, current, target);
            throw new ForbiddenException();
        }

        if (OrderStatusCd.IN_TRANSIT.equals(target)
                && OrderStatusCd.READY_TO_SHIP.equals(current)
                && (order.getExternalShipmentId() == null || order.getExternalShipmentId().isBlank())) {
            throw new ConflictException(MessageCd.INVALID_ORDER_STATUS_TRANSITION);
        }

        boolean updatedOrder = false;
        if (OrderStatusCd.IN_TRANSIT.equals(target)
                && request.trackingCode() != null && !request.trackingCode().isBlank()) {
            order.setTrackingCode(request.trackingCode().trim());
            updatedOrder = true;
        }
        if ((OrderStatusCd.CANCELLED.equals(target) || OrderStatusCd.REFUNDED.equals(target))
                && request.reason() != null && !request.reason().isBlank()) {
            order.setCancellationReason(request.reason().trim());
            updatedOrder = true;
        }
        if (updatedOrder) {
            orderRepository.save(order);
        }

        orderStatusService.move(order, target);
        return orderViewAssembler.toDetailResponse(order);
    }

    private boolean isMakerParticipatingInQuotingChat(User caller, Order order) {
        if (caller.getCompanyId() == null || order.getVendorId() != null) {
            return false;
        }
        OrderStatusCd latestStatus = orderStatusService.findLatest(order).getStatusCd();
        if (!OrderStatusCd.QUOTING.equals(latestStatus) && !OrderStatusCd.CREATED.equals(latestStatus)) {
            return false;
        }
        Optional<Company> companyOpt = companyRepository.findById(caller.getCompanyId());
        return companyOpt.isPresent()
                && chatRepository.existsByOrderGuidAndCompanyGuid(order.getGuid(), companyOpt.get().getGuid());
    }

    private boolean isBuyerTransitionAllowed(OrderStatusCd from, OrderStatusCd to) {
        return switch (from) {
            case CREATED, QUOTING -> OrderStatusCd.CANCELLED.equals(to);
            case CONFIRMED -> OrderStatusCd.CANCELLED.equals(to);
            case IN_TRANSIT -> OrderStatusCd.COMPLETED.equals(to);
            case COMPLETED, CANCELLED -> OrderStatusCd.REFUNDED.equals(to);
            default -> false;
        };
    }

    private boolean isMakerTransitionAllowed(OrderStatusCd from, OrderStatusCd to) {
        return switch (from) {
            case CONFIRMED -> OrderStatusCd.CANCELLED.equals(to);
            case IN_PRODUCTION -> OrderStatusCd.READY_TO_SHIP.equals(to) || OrderStatusCd.CANCELLED.equals(to);
            case READY_TO_SHIP -> OrderStatusCd.IN_PRODUCTION.equals(to) || OrderStatusCd.IN_TRANSIT.equals(to) || OrderStatusCd.CANCELLED.equals(to);
            case COMPLETED, CANCELLED -> OrderStatusCd.REFUNDED.equals(to);
            default -> false;
        };
    }
}
