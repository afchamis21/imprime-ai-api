package org.imprime.ai.api.service.impl;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.SaleFileResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.MakerCompanyResolver;
import org.imprime.ai.api.service.MakerOrderSupport;
import org.imprime.ai.api.service.MakerSaleFilesService;
import org.imprime.ai.api.util.PageUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MakerSaleFilesServiceImpl implements MakerSaleFilesService {
    public static final Set<OrderStatusCd> CLOSED_SALE_STATUSES = EnumSet.of(
            OrderStatusCd.CONFIRMED,
            OrderStatusCd.IN_PRODUCTION,
            OrderStatusCd.IN_TRANSIT,
            OrderStatusCd.COMPLETED
    );

    private final MakerCompanyResolver makerCompanyResolver;
    private final OrderRepository orderRepository;
    private final MakerOrderSupport makerOrderSupport;

    @Override
    public PageResponse<SaleFileResponse> listSaleFiles(String query, OrderStatusCd orderStatus, Pageable pageable) {
        Company company = makerCompanyResolver.requireActiveMakerCompany();
        List<Order> orders = orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(company.getId(), StatusCd.ACTIVE);
        if (orders == null || orders.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Map<Long, OrderStatusCd> statuses = makerOrderSupport.latestStatuses(
                orders.stream().map(Order::getId).filter(Objects::nonNull).toList());
        List<Order> closed = orders.stream()
                .filter(o -> CLOSED_SALE_STATUSES.contains(statuses.get(o.getId())))
                .filter(o -> orderStatus == null || orderStatus.equals(statuses.get(o.getId())))
                .toList();
        if (closed.isEmpty()) {
            return PageUtils.paginate(List.of(), pageable);
        }

        Map<Long, List<MakerOrderSupport.ResolvedOrderFile>> filesByOrder = makerOrderSupport.resolveFiles(closed);
        Map<Long, String> buyerNames = makerOrderSupport.buyerNames(closed);
        Map<Long, java.time.Instant> dueDates = makerOrderSupport.dueDates(closed, company);
        String normalizedQuery = query != null && !query.isBlank() ? query.trim().toLowerCase(Locale.ROOT) : null;

        List<SaleFileResponse> result = new ArrayList<>();
        for (Order order : closed) {
            List<MakerOrderSupport.ResolvedOrderFile> files = filesByOrder.getOrDefault(order.getId(), List.of());
            for (MakerOrderSupport.ResolvedOrderFile file : files) {
                String fileName = file.asset().getName() != null ? file.asset().getName() : "modelo.stl";
                SaleFileResponse response = new SaleFileResponse(
                        file.asset().getGuid(),
                        fileName,
                        file.plateName() != null ? file.plateName() : fileName,
                        file.plateNumber(),
                        extensionOf(fileName),
                        file.material(),
                        file.colorHex(),
                        file.sourceType(),
                        order.getGuid(),
                        order.getOrderCode(),
                        statuses.get(order.getId()),
                        buyerNames.get(order.getId()),
                        order.getCreateDt(),
                        dueDates.get(order.getId()),
                        files.size()
                );
                if (normalizedQuery == null || matches(response, normalizedQuery)) {
                    result.add(response);
                }
            }
        }
        return PageUtils.paginate(result, pageable);
    }

    private static boolean matches(SaleFileResponse r, String q) {
        return contains(r.fileName(), q) || contains(r.displayName(), q)
                || contains(r.orderCode(), q) || contains(r.customerName(), q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toUpperCase(Locale.ROOT)
                : "STL";
    }
}
