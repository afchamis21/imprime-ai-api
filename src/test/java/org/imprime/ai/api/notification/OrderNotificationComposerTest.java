package org.imprime.ai.api.notification;

import org.imprime.ai.api.model.Address;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.OrderItem;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MaterialType;
import org.imprime.ai.api.model.enums.OrderStatusCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.notification.composer.OrderNotificationComposer;
import org.imprime.ai.api.notification.config.EmailTemplateConfig;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.OrderPurchasedNotificationEvent;
import org.imprime.ai.api.notification.event.OrderStatusChangedNotificationEvent;
import org.imprime.ai.api.notification.event.QuoteAcceptedNotificationEvent;
import org.imprime.ai.api.notification.messaging.EmailQueueMessage;
import org.imprime.ai.api.notification.template.EmailTemplateRenderer;
import org.imprime.ai.api.repo.db.AddressRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderFileAssetRepository;
import org.imprime.ai.api.repo.db.OrderItemRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderNotificationComposerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderFileAssetRepository orderFileAssetRepository;
    @Mock private FileAssetRepository fileAssetRepository;
    @Mock private Model3DRepository model3DRepository;
    @Mock private UserRepository userRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private AddressRepository addressRepository;

    private OrderNotificationComposer composer;
    private EmailTemplateRenderer renderer;

    private User buyer;
    private User seller;
    private Company maker;
    private Order order;

    @BeforeEach
    void setUp() {
        NotificationProperties props = new NotificationProperties();
        props.setPublicBaseUrl("https://imprimeai.shop");
        composer = new OrderNotificationComposer(
                props,
                orderRepository,
                orderItemRepository,
                orderFileAssetRepository,
                fileAssetRepository,
                model3DRepository,
                userRepository,
                companyRepository,
                addressRepository
        );
        renderer = new EmailTemplateRenderer(new EmailTemplateConfig().emailTemplateEngine(), props);

        buyer = new User();
        buyer.setId(1L);
        buyer.setGuid("buyer-guid");
        buyer.setFirstName("Ana");
        buyer.setLastName("Silva");
        buyer.setEmail("ana@imprimeai.shop");
        buyer.setStatus(StatusCd.ACTIVE);

        seller = new User();
        seller.setId(2L);
        seller.setGuid("seller-guid");
        seller.setFirstName("Carlos");
        seller.setLastName("Maker");
        seller.setEmail("carlos@studio3d.com");
        seller.setStatus(StatusCd.ACTIVE);

        maker = new Company();
        maker.setId(10L);
        maker.setGuid("maker-guid");
        maker.setName("Studio Layer 3D");
        maker.setOwnerId(2L);
        maker.setStatus(StatusCd.ACTIVE);

        order = new Order();
        order.setId(100L);
        order.setGuid("abc123-def456");
        order.setOrderCode("PED-ABC123");
        order.setBuyerId(1L);
        order.setVendorId(10L);
        order.setToAddressId(55L);
        order.setShippingCost(new BigDecimal("20.00"));
        order.setOrderTotal(new BigDecimal("120.00"));
        order.setCarrierName("Correios");
        order.setTrackingCode("BR123456789");
        order.setTrackingUrl("https://www.melhorrastreio.com.br/rastreio/BR123456789");
    }

    @Test
    void buildsAndRendersPurchaseEmailsForPhysicalOrder() {
        OrderItem item = new OrderItem();
        item.setOrderId(100L);
        item.setProductId(5L);
        item.setItemName("Suporte Articulado");
        item.setMaterial(MaterialType.PETG);
        item.setColorName("Preto");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50.00"));
        item.setLineTotal(new BigDecimal("100.00"));

        Address addr = new Address();
        addr.setAddressLine1("Rua das Flores, 100");
        addr.setNeighborhood("Centro");
        addr.setCity("São Paulo");
        addr.setState("SP");
        addr.setZipCode("01000-000");

        when(orderRepository.findOrderByGuid("abc123-def456")).thenReturn(Optional.of(order));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(maker));
        when(orderItemRepository.findAllByOrderIdAndStatusOrderByIdAsc(100L, StatusCd.ACTIVE)).thenReturn(List.of(item));
        when(orderFileAssetRepository.findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(100L, StatusCd.ACTIVE)).thenReturn(List.of());
        when(addressRepository.findById(55L)).thenReturn(Optional.of(addr));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));

        List<EmailQueueMessage> messages = composer.buildPurchaseEmails(
                new OrderPurchasedNotificationEvent(100L, "abc123-def456", false, false)
        );

        assertEquals(2, messages.size());
        assertRenderedAll(messages);
    }

    @Test
    void skipsDuplicateCompletedEmailForInstantDigitalOrder() {
        OrderItem digitalItem = new OrderItem();
        digitalItem.setOrderId(100L);
        digitalItem.setModel3dId(99L);
        digitalItem.setItemName("Arquivo STL Dragão");
        digitalItem.setQuantity(1);
        digitalItem.setUnitPrice(new BigDecimal("35.00"));
        digitalItem.setLineTotal(new BigDecimal("35.00"));

        Model3D model = new Model3D();
        model.setId(99L);
        model.setAuthorUserId(2L);

        when(orderRepository.findOrderByGuid("abc123-def456")).thenReturn(Optional.of(order));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(maker));
        when(orderItemRepository.findAllByOrderIdAndStatusOrderByIdAsc(100L, StatusCd.ACTIVE)).thenReturn(List.of(digitalItem));

        List<EmailQueueMessage> skipped = composer.buildStatusChangedEmails(
                new OrderStatusChangedNotificationEvent(100L, "abc123-def456", OrderStatusCd.CONFIRMED, OrderStatusCd.COMPLETED, null, 1L)
        );
        assertTrue(skipped.isEmpty());

        when(orderFileAssetRepository.findAllByOrderIdAndStatusOrderByPlateNumberAscIdAsc(100L, StatusCd.ACTIVE)).thenReturn(List.of());
        when(model3DRepository.findAllByIdIn(List.of(99L).stream().collect(java.util.stream.Collectors.toSet()))).thenReturn(List.of(model));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));

        List<EmailQueueMessage> purchase = composer.buildPurchaseEmails(
                new OrderPurchasedNotificationEvent(100L, "abc123-def456", true, false)
        );
        assertEquals(2, purchase.size());
        assertRenderedAll(purchase);
    }

    @Test
    void buildsAndRendersQuoteAcceptedAndAllOrderStatusTemplates() {
        when(orderRepository.findOrderByGuid("abc123-def456")).thenReturn(Optional.of(order));
        when(userRepository.findUserByGuid("buyer-guid")).thenReturn(Optional.of(buyer));
        when(companyRepository.findByGuid("maker-guid")).thenReturn(Optional.of(maker));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(companyRepository.findById(10L)).thenReturn(Optional.of(maker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(seller));

        List<EmailQueueMessage> quoteMsgs = composer.buildQuoteAcceptedEmails(
                new QuoteAcceptedNotificationEvent(UUID.randomUUID(), "abc123-def456", new BigDecimal("140.00"), "buyer-guid", "buyer-guid", "maker-guid")
        );
        assertEquals(2, quoteMsgs.size());
        assertRenderedAll(quoteMsgs);

        for (OrderStatusCd status : List.of(
                OrderStatusCd.IN_PRODUCTION,
                OrderStatusCd.READY_TO_SHIP,
                OrderStatusCd.IN_TRANSIT,
                OrderStatusCd.COMPLETED,
                OrderStatusCd.CANCELLED)) {
            List<EmailQueueMessage> statusMsgs = composer.buildStatusChangedEmails(
                    new OrderStatusChangedNotificationEvent(100L, "abc123-def456", OrderStatusCd.CONFIRMED, status, "Teste", 500L)
            );
            assertFalse(statusMsgs.isEmpty(), "Esperava e-mails para status " + status);
            assertRenderedAll(statusMsgs);
        }
    }

    private void assertRenderedAll(List<EmailQueueMessage> messages) {
        for (EmailQueueMessage m : messages) {
            EmailTemplateRenderer.RenderedEmail rendered = renderer.render(m.templateName(), m.variables());
            assertNotNull(rendered.htmlBody());
            assertTrue(rendered.htmlBody().contains("Imprime"));
            assertFalse(rendered.textBody().isBlank());
        }
    }
}
