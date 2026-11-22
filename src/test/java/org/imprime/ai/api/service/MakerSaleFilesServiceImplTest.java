package org.imprime.ai.api.service;

import org.imprime.ai.api.http.response.PageResponse;
import org.imprime.ai.api.http.response.maker.SaleFileResponse;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.service.impl.MakerSaleFilesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MakerSaleFilesServiceImplTest {

    @Mock
    private MakerCompanyResolver makerCompanyResolver;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private MakerOrderSupport makerOrderSupport;

    @InjectMocks
    private MakerSaleFilesServiceImpl service;

    private Company company;
    private Order inProduction;
    private Order completed;
    private Order created;
    private Order inTransit;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);
        company.setGuid("comp-10");

        inProduction = order(1L, "PED-001");
        completed = order(2L, "PED-002");
        created = order(3L, "PED-003");
        inTransit = order(4L, "PED-004");
    }

    @Test
    void listSaleFilesReturnsOnlyFilesFromClosedSalesWithSaleMetadata() {
        stubOrders();
        when(makerOrderSupport.latestStatuses(any())).thenReturn(Map.of(
                1L, OrderStatusCd.IN_PRODUCTION,
                2L, OrderStatusCd.COMPLETED,
                3L, OrderStatusCd.CREATED,
                4L, OrderStatusCd.IN_TRANSIT
        ));
        when(makerOrderSupport.resolveFiles(any())).thenAnswer(invocation ->
                filesForOrders(invocation.getArgument(0)));
        when(makerOrderSupport.buyerNames(any())).thenReturn(Map.of(1L, "Ana Souza", 2L, "Bruno Lima"));
        Instant due1 = Instant.parse("2026-12-01T10:00:00Z");
        Instant due2 = Instant.parse("2026-12-02T10:00:00Z");
        when(makerOrderSupport.dueDates(any(), eq(company))).thenReturn(Map.of(1L, due1, 2L, due2));

        PageResponse<SaleFileResponse> page = service.listSaleFiles(null, null, PageRequest.of(0, 10));

        assertEquals(3, page.totalElements());
        SaleFileResponse first = page.content().getFirst();
        assertEquals("fa-capacete", first.fileGuid());
        assertEquals("capacete.stl", first.fileName());
        assertEquals("STL", first.format());
        assertEquals("ORDER_PLATE", first.sourceType());
        assertEquals(1, first.plateNumber());
        assertEquals("ord-1", first.orderGuid());
        assertEquals("PED-001", first.orderCode());
        assertEquals(OrderStatusCd.IN_PRODUCTION, first.orderStatus());
        assertEquals("Ana Souza", first.customerName());
        assertEquals(due1, first.dueAt());
        assertEquals(2, first.orderFilesCount());

        SaleFileResponse second = page.content().get(1);
        assertEquals("fa-extra", second.fileGuid());

        SaleFileResponse third = page.content().get(2);
        assertEquals("fa-suporte", third.fileGuid());
        assertEquals("3MF", third.format());
        assertEquals(OrderStatusCd.COMPLETED, third.orderStatus());
    }

    @Test
    void listSaleFilesFiltersByOrderStatusAndPagination() {
        stubOrders();
        when(makerOrderSupport.latestStatuses(any())).thenReturn(Map.of(
                1L, OrderStatusCd.IN_PRODUCTION,
                2L, OrderStatusCd.COMPLETED,
                3L, OrderStatusCd.CREATED,
                4L, OrderStatusCd.IN_TRANSIT
        ));
        when(makerOrderSupport.resolveFiles(any())).thenAnswer(invocation ->
                filesForOrders(invocation.getArgument(0)));
        when(makerOrderSupport.buyerNames(any())).thenReturn(Map.of());
        when(makerOrderSupport.dueDates(any(), eq(company))).thenReturn(Map.of());

        PageResponse<SaleFileResponse> completedOnly = service.listSaleFiles(
                null, OrderStatusCd.COMPLETED, PageRequest.of(0, 10));
        assertEquals(1, completedOnly.totalElements());
        assertEquals("fa-suporte", completedOnly.content().getFirst().fileGuid());

        PageResponse<SaleFileResponse> firstPage = service.listSaleFiles(
                null, null, PageRequest.of(0, 1));
        assertEquals(3, firstPage.totalElements());
        assertEquals(3, firstPage.totalPages());
        assertEquals(1, firstPage.content().size());
    }

    @Test
    void listSaleFilesSearchesByFilePlateOrderCodeAndCustomer() {
        stubOrders();
        when(makerOrderSupport.latestStatuses(any())).thenReturn(Map.of(
                1L, OrderStatusCd.IN_PRODUCTION,
                2L, OrderStatusCd.COMPLETED,
                3L, OrderStatusCd.CREATED,
                4L, OrderStatusCd.IN_TRANSIT
        ));
        when(makerOrderSupport.resolveFiles(any())).thenAnswer(invocation ->
                filesForOrders(invocation.getArgument(0)));
        when(makerOrderSupport.buyerNames(any())).thenAnswer(invocation -> Map.of(1L, "Ana Souza", 2L, "Bruno Lima"));
        when(makerOrderSupport.dueDates(any(), eq(company))).thenReturn(Map.of());

        assertEquals(1, service.listSaleFiles("capacete", null, PageRequest.of(0, 10)).totalElements());
        assertEquals(1, service.listSaleFiles("PED-002", null, PageRequest.of(0, 10)).totalElements());
        assertEquals(1, service.listSaleFiles("bruno", null, PageRequest.of(0, 10)).totalElements());
        assertEquals(0, service.listSaleFiles("inexistente", null, PageRequest.of(0, 10)).totalElements());
    }

    private void stubOrders() {
        when(makerCompanyResolver.requireActiveMakerCompany()).thenReturn(company);
        when(orderRepository.findAllByVendorIdAndStatusOrderByCreateDtDesc(10L, StatusCd.ACTIVE))
                .thenReturn(List.of(inProduction, completed, created, inTransit));
    }

    private Map<Long, List<MakerOrderSupport.ResolvedOrderFile>> filesForOrders(Collection<Order> orders) {
        Map<Long, List<MakerOrderSupport.ResolvedOrderFile>> result = new LinkedHashMap<>();
        List<Order> list = new ArrayList<>(orders);
        for (Order order : list) {
            if (order.getId() == 1L) {
                result.put(1L, List.of(
                        new MakerOrderSupport.ResolvedOrderFile(1L, asset("fa-capacete", "capacete.stl"),
                                1, "Mesa 1 — Capacete", "PLA", "#ffffff", "ORDER_PLATE"),
                        new MakerOrderSupport.ResolvedOrderFile(1L, asset("fa-extra", "extra.glb"),
                                2, "Mesa 2 — Extra", "PETG", "#000000", "ORDER_PLATE")
                ));
            } else if (order.getId() == 2L) {
                result.put(2L, List.of(
                        new MakerOrderSupport.ResolvedOrderFile(2L, asset("fa-suporte", "suporte.3mf"),
                                1, "Suporte", "PETG", null, "MODEL_3D")
                ));
            } else {
                result.put(order.getId(), List.of());
            }
        }
        return result;
    }

    private static FileAsset asset(String guid, String name) {
        FileAsset asset = new FileAsset();
        asset.setGuid(guid);
        asset.setName(name);
        return asset;
    }

    private static Order order(long id, String code) {
        Order order = new Order();
        order.setId(id);
        order.setGuid("ord-" + id);
        order.setOrderCode(code);
        order.setVendorId(10L);
        order.setBuyerId(id + 100);
        order.setCreateDt(Instant.parse("2026-11-20T12:00:00Z"));
        return order;
    }
}
