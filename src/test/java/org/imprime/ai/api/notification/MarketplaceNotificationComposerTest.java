package org.imprime.ai.api.notification;

import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.Model3D;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.Product;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.notification.composer.AccountNotificationComposer;
import org.imprime.ai.api.notification.composer.MarketplaceNotificationComposer;
import org.imprime.ai.api.notification.config.EmailTemplateConfig;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.IpNoticeReceiptNotificationEvent;
import org.imprime.ai.api.notification.event.ModerationNotificationEvent;
import org.imprime.ai.api.notification.event.PayoutNotificationEvent;
import org.imprime.ai.api.notification.event.RefundDecisionNotificationEvent;
import org.imprime.ai.api.notification.event.ReviewActivityNotificationEvent;
import org.imprime.ai.api.notification.event.SecurityEmailNotificationEvent;
import org.imprime.ai.api.notification.event.SecurityEmailNotificationEvent.SecurityEmailType;
import org.imprime.ai.api.notification.event.SupportTicketNotificationEvent;
import org.imprime.ai.api.notification.event.UserRegisteredNotificationEvent;
import org.imprime.ai.api.notification.messaging.EmailQueueMessage;
import org.imprime.ai.api.notification.preferences.NotificationPreferenceService;
import org.imprime.ai.api.notification.template.EmailTemplateRenderer;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.Model3DRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.ProductRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceNotificationComposerTest {

    @Mock
    private NotificationPreferenceService preferenceService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private Model3DRepository model3DRepository;

    private NotificationProperties properties;
    private AccountNotificationComposer accountComposer;
    private MarketplaceNotificationComposer marketplaceComposer;
    private EmailTemplateRenderer renderer;

    private User buyer;
    private User makerUser;
    private User designerUser;
    private Company makerCompany;
    private Order order;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setPublicBaseUrl("https://imprimeai.shop");

        accountComposer = new AccountNotificationComposer(properties, userRepository, companyRepository);
        marketplaceComposer = new MarketplaceNotificationComposer(
                properties,
                preferenceService,
                userRepository,
                companyRepository,
                orderRepository,
                productRepository,
                model3DRepository
        );
        renderer = new EmailTemplateRenderer(new EmailTemplateConfig().emailTemplateEngine(), properties);

        buyer = new User();
        buyer.setId(1L);
        buyer.setGuid("buyer-1");
        buyer.setFirstName("Carlos");
        buyer.setLastName("Mendes");
        buyer.setEmail("carlos@example.com");
        buyer.setRole(UserRole.BUYER);
        buyer.setStatus(StatusCd.ACTIVE);

        makerUser = new User();
        makerUser.setId(2L);
        makerUser.setGuid("maker-2");
        makerUser.setFirstName("Sofia");
        makerUser.setLastName("Almeida");
        makerUser.setEmail("sofia@maker3d.com");
        makerUser.setCompanyId(20L);
        makerUser.setRole(UserRole.MAKER);
        makerUser.setStatus(StatusCd.ACTIVE);

        designerUser = new User();
        designerUser.setId(3L);
        designerUser.setGuid("designer-3");
        designerUser.setFirstName("Rafael");
        designerUser.setLastName("Costa");
        designerUser.setEmail("rafael@design.com");
        designerUser.setRole(UserRole.DESIGNER);
        designerUser.setStatus(StatusCd.ACTIVE);

        makerCompany = new Company();
        makerCompany.setId(20L);
        makerCompany.setGuid("comp-20");
        makerCompany.setName("Maker3D Lab");
        makerCompany.setSlug("maker3d-lab");
        makerCompany.setOwnerId(2L);

        order = new Order();
        order.setId(99L);
        order.setGuid("ord-99-guid");
        order.setOrderCode("PED-0099");
    }

    @Test
    void rendersAllAccountAndSecurityTemplatesCleanly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(makerUser));
        when(companyRepository.findById(20L)).thenReturn(Optional.of(makerCompany));

        List<EmailQueueMessage> buyerWelcome = accountComposer.buildWelcomeEmail(new UserRegisteredNotificationEvent(1L));
        List<EmailQueueMessage> makerWelcome = accountComposer.buildWelcomeEmail(new UserRegisteredNotificationEvent(2L));
        List<EmailQueueMessage> verify = accountComposer.buildSecurityEmail(new SecurityEmailNotificationEvent(
                SecurityEmailType.EMAIL_VERIFICATION, 1L, buyer.getEmail(), buyer.getFirstName(),
                "verify+tok", Instant.parse("2026-11-28T14:00:00Z")));
        List<EmailQueueMessage> reset = accountComposer.buildSecurityEmail(new SecurityEmailNotificationEvent(
                SecurityEmailType.PASSWORD_RESET, 1L, buyer.getEmail(), buyer.getFirstName(),
                "reset+tok", Instant.parse("2026-11-27T14:30:00Z")));
        List<EmailQueueMessage> changed = accountComposer.buildSecurityEmail(new SecurityEmailNotificationEvent(
                SecurityEmailType.PASSWORD_CHANGED, 1L, buyer.getEmail(), buyer.getFirstName(),
                null, Instant.parse("2026-11-27T14:10:00Z")));

        assertRenderedOk(buyerWelcome.get(0), "marketplace");
        assertRenderedOk(makerWelcome.get(0), "Maker3D Lab");
        assertRenderedOk(verify.get(0), "/verify-email?token=verify%2Btok");
        assertRenderedOk(reset.get(0), "/reset-password?token=reset%2Btok");
        assertRenderedOk(changed.get(0), "alterada");
    }

    @Test
    void rendersAllFinanceSupportModerationAndLegalTemplatesCleanly() {
        when(orderRepository.findById(99L)).thenReturn(Optional.of(order));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findById(3L)).thenReturn(Optional.of(designerUser));
        when(userRepository.findAllByCompanyId(20L)).thenReturn(List.of(makerUser));

        List<EmailQueueMessage> approvedRefund = marketplaceComposer.buildRefundEmails(
                new RefundDecisionNotificationEvent("ref-1", 99L, 1L, 20L, true,
                        new BigDecimal("189.90"), "Peça danificada", "Estorno integral autorizado", "FULL"));
        assertEquals(2, approvedRefund.size());
        assertRenderedOk(approvedRefund.get(0), "189,90");
        assertRenderedOk(approvedRefund.get(1), "PED-0099");

        List<EmailQueueMessage> rejectedRefund = marketplaceComposer.buildRefundEmails(
                new RefundDecisionNotificationEvent("ref-2", 99L, 1L, 20L, false,
                        new BigDecimal("189.90"), "Fora do prazo de garantia", null, "FULL"));
        assertRenderedOk(rejectedRefund.get(0), "Fora do prazo");

        List<EmailQueueMessage> payoutReq = marketplaceComposer.buildPayoutEmails(
                new PayoutNotificationEvent("pay-1", "MAKER", 20L, null, "REQUESTED",
                        new BigDecimal("540.00"), "***.123.456-**", "IDEM-1", null));
        List<EmailQueueMessage> payoutComp = marketplaceComposer.buildPayoutEmails(
                new PayoutNotificationEvent("pay-2", "DESIGNER", null, 3L, "COMPLETED",
                        new BigDecimal("120.50"), "raf***@design.com", "PIX-99", null));
        List<EmailQueueMessage> payoutFail = marketplaceComposer.buildPayoutEmails(
                new PayoutNotificationEvent("pay-3", "MAKER", 20L, null, "FAILED",
                        new BigDecimal("300.00"), "***.123.456-**", "IDEM-2", "Chave PIX divergente"));
        assertRenderedOk(payoutReq.get(0), "540,00");
        assertRenderedOk(payoutComp.get(0), "120,50");
        assertRenderedOk(payoutFail.get(0), "Chave PIX divergente");

        List<EmailQueueMessage> ticketOpen = marketplaceComposer.buildSupportTicketEmails(
                new SupportTicketNotificationEvent("tkt-1", "SUP-2026-001", 1L,
                        "Atraso na coleta", "ORDER", "OPEN", false, "OPENED", null, null));
        List<EmailQueueMessage> ticketReply = marketplaceComposer.buildSupportTicketEmails(
                new SupportTicketNotificationEvent("tkt-1", "SUP-2026-001", 1L,
                        "Atraso na coleta", "ORDER", "WAITING_USER", false, "REPLIED",
                        "Já acionamos a transportadora.", null));
        assertRenderedOk(ticketOpen.get(0), "SUP-2026-001");
        assertRenderedOk(ticketReply.get(0), "Já acionamos a transportadora.");

        List<EmailQueueMessage> modMaker = marketplaceComposer.buildModerationEmails(
                new ModerationNotificationEvent("MAKER", "comp-20", null, 20L,
                        "Maker3D Lab", "APPROVED", "Documentação validada", null, null));
        List<EmailQueueMessage> modModel = marketplaceComposer.buildModerationEmails(
                new ModerationNotificationEvent("MODEL", "mod-1", 3L, null,
                        "Suporte Articulado", "TAKEDOWN_DMCA", "Infração de desenho industrial",
                        "Empresa Titular LTDA", "DMCA-987"));
        buyer.setStatus(StatusCd.SUSPENDED);
        List<EmailQueueMessage> modUser = marketplaceComposer.buildModerationEmails(
                new ModerationNotificationEvent("USER", "buyer-1", 1L, null,
                        "Carlos Mendes", "SUSPENDED", "Violação reiterada", null, null));
        assertRenderedOk(modMaker.get(0), "Aprovado");
        assertRenderedOk(modModel.get(0), "DMCA-987");
        assertRenderedOk(modUser.get(0), "Violação reiterada");

        List<EmailQueueMessage> ipReceipt = marketplaceComposer.buildIpNoticeReceiptEmails(
                new IpNoticeReceiptNotificationEvent("IP-2026-777", "track-guid-777",
                        "Advocacia PI", "legal@titular.com", "Suporte Articulado", "MODEL"));
        assertRenderedOk(ipReceipt.get(0), "IP-2026-777");
    }

    @Test
    void respectsReviewsPreferenceAndSkipsSelfReplies() {
        Product product = new Product();
        product.setId(55L);
        product.setCompanyId(20L);
        product.setName("Organizador Modular");
        product.setSlug("organizador-modular");

        when(productRepository.findById(55L)).thenReturn(Optional.of(product));
        when(userRepository.findById(1L)).thenReturn(Optional.of(buyer));
        when(userRepository.findAllByCompanyId(20L)).thenReturn(List.of(makerUser));
        when(preferenceService.isReviewsEnabled(2L)).thenReturn(false);

        List<EmailQueueMessage> optedOut = marketplaceComposer.buildReviewActivityEmails(
                new ReviewActivityNotificationEvent("rev-1", "PRODUCT", 55L, 1L, 1L, false, 5, "Acabamento impecável!"));
        assertTrue(optedOut.isEmpty());

        when(preferenceService.isReviewsEnabled(2L)).thenReturn(true);
        List<EmailQueueMessage> allowed = marketplaceComposer.buildReviewActivityEmails(
                new ReviewActivityNotificationEvent("rev-1", "PRODUCT", 55L, 1L, 1L, false, 5, "Acabamento impecável!"));
        assertEquals(1, allowed.size());
        assertRenderedOk(allowed.get(0), "Organizador Modular");

        // Resposta do próprio autor na própria avaliação não dispara e-mail
        List<EmailQueueMessage> selfReply = marketplaceComposer.buildReviewActivityEmails(
                new ReviewActivityNotificationEvent("rev-1", "PRODUCT", 55L, 1L, 1L, true, 5, "Editando"));
        assertTrue(selfReply.isEmpty());

        Model3D model = new Model3D();
        model.setId(66L);
        model.setAuthorUserId(3L);
        model.setName("Engrenagem Helicoide");
        model.setSlug("engrenagem-helicoide");
        when(model3DRepository.findById(66L)).thenReturn(Optional.of(model));
        when(userRepository.findById(3L)).thenReturn(Optional.of(designerUser));
        when(preferenceService.isReviewsEnabled(1L)).thenReturn(true);

        List<EmailQueueMessage> replyToBuyer = marketplaceComposer.buildReviewActivityEmails(
                new ReviewActivityNotificationEvent("rev-2", "MODEL", 66L, 3L, 1L, true, 4, "Obrigado pelo feedback!"));
        assertEquals(1, replyToBuyer.size());
        assertRenderedOk(replyToBuyer.get(0), "Engrenagem Helicoide");
    }

    private void assertRenderedOk(EmailQueueMessage message, String expectedFragment) {
        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(message.templateName(), message.variables());
        assertFalse(rendered.htmlBody().isBlank());
        assertFalse(rendered.textBody().isBlank());
        assertTrue(rendered.htmlBody().contains(expectedFragment),
                () -> "Expected '" + expectedFragment + "' in template " + message.templateName());
    }
}
