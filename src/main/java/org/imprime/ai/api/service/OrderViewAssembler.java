package org.imprime.ai.api.service;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.common.MakerBriefResponse;
import org.imprime.ai.api.http.response.common.OrderItemResponse;
import org.imprime.ai.api.http.response.common.OrderSummaryResponse;
import org.imprime.ai.api.http.response.order.OrderDetailResponse;
import org.imprime.ai.api.http.response.order.OrderStatusHistoryResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.AddressDTO;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.OrderItemRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderViewAssembler {
    private final OrderStatusService orderStatusService;
    private final AddressService addressService;
    private final UserService userService;
    private final CompanyRepository companyRepository;
    private final FileAssetRepository fileAssetRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;

    public OrderSummaryResponse toSummaryResponse(Order order) {
        OrderHeaderView header = buildHeaderView(order);
        return new OrderSummaryResponse(
                order.getGuid(),
                header.orderCode(),
                header.currentStatus(),
                header.subtotal(),
                header.shippingCost(),
                header.totalAmount(),
                order.getTrackingCode(),
                order.getCarrierName(),
                order.getTrackingStatus(),
                order.getTrackingUrl(),
                order.getNotes(),
                header.maker(),
                header.buyer(),
                header.items(),
                order.getCreateDt(),
                order.getUpdateDt()
        );
    }

    public OrderDetailResponse toDetailResponse(Order order) {
        OrderHeaderView header = buildHeaderView(order);
        AddressDTO shippingAddress = order.getToAddressId() != null
                ? addressService.findById(order.getToAddressId()).map(AddressDTO::from).orElse(null)
                : null;

        List<OrderStatusHistoryResponse> statusHistory = orderStatusService.findHistory(order).stream()
                .map(os -> new OrderStatusHistoryResponse(os.getStatusCd(), os.getStatusDate(), os.getCreateUser()))
                .toList();

        return new OrderDetailResponse(
                order.getGuid(),
                header.orderCode(),
                header.currentStatus(),
                header.subtotal(),
                header.shippingCost(),
                header.totalAmount(),
                order.getTrackingCode(),
                order.getCarrierName(),
                order.getTrackingStatus(),
                order.getTrackingUrl(),
                order.getNotes(),
                shippingAddress,
                header.maker(),
                header.buyer(),
                header.items(),
                statusHistory,
                order.getCreateDt(),
                order.getUpdateDt(),
                order.getPaymentConfirmedAt()
        );
    }

    public PageResponse<OrderSummaryResponse> filterAndPaginate(
            List<Order> orders,
            OrderStatusCd statusFilter,
            String queryFilter,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        String normalizedQuery = queryFilter != null && !queryFilter.isBlank()
                ? queryFilter.trim().toLowerCase(Locale.ROOT)
                : null;

        List<OrderSummaryResponse> filtered = orders.stream()
                .filter(order -> matchesDateRange(order, fromDate, toDate))
                .map(this::toSummaryResponse)
                .filter(summary -> statusFilter == null || statusFilter.equals(summary.status()))
                .filter(summary -> matchesQuery(summary, normalizedQuery))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<OrderSummaryResponse> slice = start >= filtered.size() ? List.of() : filtered.subList(start, end);

        Page<OrderSummaryResponse> page = new PageImpl<>(slice, pageable, filtered.size());
        return PageResponse.from(page);
    }

    public String formatOrderCode(String orderGuid) {
        if (orderGuid == null || orderGuid.isBlank()) {
            return "PED-000000";
        }
        String compact = orderGuid.replace("-", "");
        String suffix = compact.length() >= 6 ? compact.substring(0, 6) : compact;
        return "PED-" + suffix.toUpperCase(Locale.ROOT);
    }

    public MakerBriefResponse toMakerBrief(Company company) {
        if (company == null) {
            return null;
        }
        return new MakerBriefResponse(
                company.getGuid(),
                company.getSlug(),
                company.getName(),
                company.getAvatarUrl(),
                company.getRatingAvg() != null ? company.getRatingAvg() : BigDecimal.ZERO,
                company.getReviewsCount() != null ? company.getReviewsCount() : 0,
                Boolean.TRUE.equals(company.getVerified())
        );
    }

    private OrderHeaderView buildHeaderView(Order order) {
        OrderStatusCd currentStatus = orderStatusService.findLatest(order).getStatusCd();
        String orderCode = order.getOrderCode() != null && !order.getOrderCode().isBlank()
                ? order.getOrderCode()
                : formatOrderCode(order.getGuid());
        UserDTO buyer = resolveBuyer(order.getBuyerId());
        MakerBriefResponse maker = resolveMaker(order.getVendorId());
        BigDecimal shippingCost = order.getShippingCost() != null ? order.getShippingCost() : BigDecimal.ZERO;

        List<OrderItem> persistedItems = order.getId() != null
                ? orderItemRepository.findAllByOrderIdAndStatusOrderByIdAsc(order.getId(), StatusCd.ACTIVE)
                : List.of();

        List<OrderItemResponse> items;
        BigDecimal subtotal;
        BigDecimal totalAmount;

        if (!persistedItems.isEmpty()) {
            items = mapOrderItems(persistedItems);
            subtotal = items.stream()
                    .map(item -> item.lineTotal() != null ? item.lineTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalAmount = order.getOrderTotal() != null ? order.getOrderTotal() : subtotal.add(shippingCost);
        } else {
            totalAmount = order.getOrderTotal() != null ? order.getOrderTotal() : BigDecimal.ZERO;
            subtotal = totalAmount.compareTo(shippingCost) >= 0 ? totalAmount.subtract(shippingCost) : totalAmount;
            items = buildFallbackAssetItem(order, subtotal);
        }

        return new OrderHeaderView(orderCode, currentStatus, subtotal, shippingCost, totalAmount, maker, buyer, items);
    }

    private List<OrderItemResponse> mapOrderItems(List<OrderItem> persistedItems) {
        Set<Long> productIds = persistedItems.stream()
                .map(OrderItem::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> productGuidsById = productIds.isEmpty()
                ? Map.of()
                : productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getGuid, (a, b) -> a));

        return persistedItems.stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId() != null ? productGuidsById.get(item.getProductId()) : null,
                        item.getItemName(),
                        item.getMaterial(),
                        item.getColorName(),
                        item.getQuantity() != null ? item.getQuantity() : 1,
                        item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO,
                        item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO
                ))
                .toList();
    }

    private List<OrderItemResponse> buildFallbackAssetItem(Order order, BigDecimal unitPrice) {
        if (order.getFileAssetId() == null) {
            return List.of();
        }
        String assetName = fileAssetRepository.findById(order.getFileAssetId())
                .map(FileAsset::getName)
                .orElse("Impressão 3D sob demanda");
        return List.of(new OrderItemResponse(
                null,
                assetName,
                null,
                null,
                1,
                unitPrice,
                unitPrice
        ));
    }

    private boolean matchesDateRange(Order order, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null && toDate == null) {
            return true;
        }
        if (order.getCreateDt() == null) {
            return false;
        }
        LocalDate createdDate = order.getCreateDt().atZone(ZoneOffset.UTC).toLocalDate();
        if (fromDate != null && createdDate.isBefore(fromDate)) {
            return false;
        }
        if (toDate != null && createdDate.isAfter(toDate)) {
            return false;
        }
        return true;
    }

    private boolean matchesQuery(OrderSummaryResponse summary, String normalizedQuery) {
        if (normalizedQuery == null) {
            return true;
        }
        if (containsIgnoreCase(summary.orderCode(), normalizedQuery)
                || containsIgnoreCase(summary.orderGuid(), normalizedQuery)) {
            return true;
        }
        if (summary.maker() != null && containsIgnoreCase(summary.maker().name(), normalizedQuery)) {
            return true;
        }
        if (summary.buyer() != null && (containsIgnoreCase(summary.buyer().firstName(), normalizedQuery)
                || containsIgnoreCase(summary.buyer().lastName(), normalizedQuery))) {
            return true;
        }
        return summary.items().stream().anyMatch(item -> containsIgnoreCase(item.name(), normalizedQuery));
    }

    private boolean containsIgnoreCase(String value, String queryLower) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(queryLower);
    }

    private UserDTO resolveBuyer(Long buyerId) {
        if (buyerId == null) {
            return null;
        }
        return userService.findUserById(buyerId).map(UserDTO::from).orElse(null);
    }

    private MakerBriefResponse resolveMaker(Long vendorId) {
        if (vendorId == null) {
            return null;
        }
        return companyRepository.findById(vendorId).map(this::toMakerBrief).orElse(null);
    }

    private record OrderHeaderView(
            String orderCode,
            OrderStatusCd currentStatus,
            BigDecimal subtotal,
            BigDecimal shippingCost,
            BigDecimal totalAmount,
            MakerBriefResponse maker,
            UserDTO buyer,
            List<OrderItemResponse> items
    ) {
    }
}
