package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderFileAsset;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderItemRepository;
import org.imprime.ai.api.repo.db.OrderStatusRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utilitários de leitura em lote sobre pedidos (vendas) de um Maker: status atual,
 * prazo de entrega, nome do comprador e arquivos 3D associados.
 */
@Component
@RequiredArgsConstructor
public class MakerOrderSupport {
    public static final int DEFAULT_LEAD_TIME_DAYS = 5;

    private final OrderStatusRepository orderStatusRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderFileAssetRepository orderFileAssetRepository;
    private final ProductRepository productRepository;
    private final Model3DRepository model3DRepository;
    private final FileAssetRepository fileAssetRepository;
    private final UserRepository userRepository;

    public record ResolvedOrderFile(
            Long orderId,
            FileAsset asset,
            int plateNumber,
            String plateName,
            String material,
            String colorHex,
            String sourceType
    ) {
    }

    public Map<Long, OrderStatusCd> latestStatuses(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, OrderStatusCd> result = new HashMap<>();
        for (OrderStatusRepository.LatestOrderStatusView view : orderStatusRepository.findLatestStatusesByOrderIds(orderIds)) {
            OrderStatusCd cd = fromCode(view.getStatusCd());
            if (view.getOrderId() != null && cd != null) {
                result.put(view.getOrderId(), cd);
            }
        }
        return result;
    }

    public static OrderStatusCd fromCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (OrderStatusCd cd : OrderStatusCd.values()) {
            if (cd.getCode().equals(trimmed) || cd.name().equalsIgnoreCase(trimmed)) {
                return cd;
            }
        }
        return null;
    }

    /**
     * Prazo de entrega por pedido: data da venda + maior prazo de produção dos produtos
     * do pedido; na ausência de produtos, usa o prazo máximo configurado pelo estúdio.
     */
    public Map<Long, Instant> dueDates(Collection<Order> orders, Company company) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> orderIds = orders.stream().map(Order::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<OrderItem> items = orderIds.isEmpty()
                ? List.of()
                : orderItemRepository.findAllByOrderIdInAndStatus(orderIds, StatusCd.ACTIVE);

        Set<Long> productIds = items.stream().map(OrderItem::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Product> productsById = productIds.isEmpty()
                ? Collections.emptyMap()
                : productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        Map<Long, Integer> productionDaysByOrder = new HashMap<>();
        for (OrderItem item : items) {
            Product product = item.getProductId() != null ? productsById.get(item.getProductId()) : null;
            if (product != null && product.getProductionDays() != null && product.getProductionDays() > 0) {
                productionDaysByOrder.merge(item.getOrderId(), product.getProductionDays(), Math::max);
            }
        }

        int companyLead = company != null && company.getMaxLeadTimeDays() != null && company.getMaxLeadTimeDays() > 0
                ? company.getMaxLeadTimeDays()
                : DEFAULT_LEAD_TIME_DAYS;

        Map<Long, Instant> result = new HashMap<>();
        for (Order order : orders) {
            Instant base = order.getCreateDt() != null ? order.getCreateDt() : Instant.now();
            int days = productionDaysByOrder.getOrDefault(order.getId(), companyLead);
            result.put(order.getId(), base.plus(Duration.ofDays(days)));
        }
        return result;
    }

    public Map<Long, String> buyerNames(Collection<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> buyerIds = orders.stream().map(Order::getBuyerId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (buyerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> namesByUser = new HashMap<>();
        for (User user : userRepository.findAllById(buyerIds)) {
            String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                    + (user.getLastName() != null ? user.getLastName() : "")).trim();
            namesByUser.put(user.getId(), name.isBlank() ? null : name);
        }
        Map<Long, String> result = new HashMap<>();
        for (Order order : orders) {
            String name = namesByUser.get(order.getBuyerId());
            if (name != null) {
                result.put(order.getId(), name);
            }
        }
        return result;
    }

    /**
     * Resolve, em lote, os arquivos 3D de cada pedido: mesas (ORDER_FILE_ASSET), arquivo legado
     * do pedido e modelos 3D comprados no marketplace (ORDER_ITEM → MODEL_3D).
     */
    public Map<Long, List<ResolvedOrderFile>> resolveFiles(Collection<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> orderIds = orders.stream().map(Order::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<OrderFileAsset> plates = orderFileAssetRepository
                .findAllByOrderIdInAndStatusOrderByPlateNumberAscIdAsc(orderIds, StatusCd.ACTIVE);
        List<OrderItem> items = orderItemRepository.findAllByOrderIdInAndStatus(orderIds, StatusCd.ACTIVE);
        Set<Long> modelIds = items.stream().map(OrderItem::getModel3dId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Model3D> modelsById = modelIds.isEmpty()
                ? Collections.emptyMap()
                : model3DRepository.findAllByIdIn(modelIds).stream()
                .collect(Collectors.toMap(Model3D::getId, m -> m, (a, b) -> a));

        Set<Long> fileIds = new java.util.HashSet<>();
        plates.forEach(p -> fileIds.add(p.getFileAssetId()));
        orders.forEach(o -> {
            if (o.getFileAssetId() != null) {
                fileIds.add(o.getFileAssetId());
            }
        });
        modelsById.values().forEach(m -> {
            if (m.getFileAssetId() != null) {
                fileIds.add(m.getFileAssetId());
            }
        });
        fileIds.remove(null);
        Map<Long, FileAsset> filesById = fileIds.isEmpty()
                ? Collections.emptyMap()
                : fileAssetRepository.findAllByIdIn(fileIds).stream()
                .collect(Collectors.toMap(FileAsset::getId, f -> f, (a, b) -> a));

        Map<Long, List<OrderFileAsset>> platesByOrder = plates.stream()
                .collect(Collectors.groupingBy(OrderFileAsset::getOrderId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<OrderItem>> itemsByOrder = items.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, List<ResolvedOrderFile>> result = new HashMap<>();
        for (Order order : orders) {
            List<ResolvedOrderFile> files = new ArrayList<>();
            Set<Long> seen = new java.util.HashSet<>();
            int index = 1;
            for (OrderFileAsset plate : platesByOrder.getOrDefault(order.getId(), List.of())) {
                FileAsset asset = filesById.get(plate.getFileAssetId());
                if (asset == null || !seen.add(asset.getId())) {
                    continue;
                }
                files.add(new ResolvedOrderFile(
                        order.getId(),
                        asset,
                        plate.getPlateNumber() != null ? plate.getPlateNumber() : index,
                        plate.getPlateName() != null && !plate.getPlateName().isBlank() ? plate.getPlateName() : asset.getName(),
                        plate.getMaterial(),
                        plate.getColorHex(),
                        "ORDER_PLATE"
                ));
                index++;
            }
            if (files.isEmpty() && order.getFileAssetId() != null) {
                FileAsset asset = filesById.get(order.getFileAssetId());
                if (asset != null && seen.add(asset.getId())) {
                    files.add(new ResolvedOrderFile(order.getId(), asset, index++,
                            asset.getName() != null ? asset.getName() : "Mesa 1", null, null, "ORDER_FILE"));
                }
            }
            for (OrderItem item : itemsByOrder.getOrDefault(order.getId(), List.of())) {
                Model3D model = item.getModel3dId() != null ? modelsById.get(item.getModel3dId()) : null;
                FileAsset asset = model != null && model.getFileAssetId() != null ? filesById.get(model.getFileAssetId()) : null;
                if (asset == null || !seen.add(asset.getId())) {
                    continue;
                }
                files.add(new ResolvedOrderFile(
                        order.getId(),
                        asset,
                        index++,
                        item.getItemName() != null && !item.getItemName().isBlank() ? item.getItemName() : model.getName(),
                        item.getMaterial() != null ? item.getMaterial().name() : null,
                        null,
                        "MODEL_3D"
                ));
            }
            result.put(order.getId(), files);
        }
        return result;
    }
}
