package org.imprime.ai.api.notification;

import com.github.benmanes.caffeine.cache.Ticker;
import org.imprime.ai.api.model.Chat;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.FileAsset;
import org.imprime.ai.api.model.Order;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.notification.chat.ChatActiveViewCache;
import org.imprime.ai.api.notification.chat.ChatEmailDigestScheduler;
import org.imprime.ai.api.notification.chat.ChatParticipantState;
import org.imprime.ai.api.notification.chat.ChatParticipantStateId;
import org.imprime.ai.api.notification.chat.ChatParticipantStateRepository;
import org.imprime.ai.api.notification.composer.ChatNotificationComposer;
import org.imprime.ai.api.notification.config.EmailTemplateConfig;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.ChatDigestReadyEvent;
import org.imprime.ai.api.notification.messaging.EmailCategory;
import org.imprime.ai.api.notification.messaging.EmailQueueMessage;
import org.imprime.ai.api.notification.preferences.NotificationPreferenceService;
import org.imprime.ai.api.notification.template.EmailTemplateRenderer;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.ChatRepository;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.FileAssetRepository;
import org.imprime.ai.api.repo.db.OrderRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatEmailDigestSchedulerTest {

    @Mock
    private ChatParticipantStateRepository stateRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationPreferenceService preferenceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private FileAssetRepository fileAssetRepository;

    private NotificationProperties properties;
    private ChatActiveViewCache activeViewCache;
    private ChatEmailDigestScheduler scheduler;
    private ChatNotificationComposer composer;
    private EmailTemplateRenderer renderer;
    private Instant now;
    private UUID chatId;
    private User buyer;
    private User makerUser;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-27T12:10:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);

        properties = new NotificationProperties();
        properties.setPublicBaseUrl("https://imprimeai.shop");
        properties.getChat().setMaxActiveDeferral(Duration.ofMinutes(15));
        properties.getChat().setBatchSize(50);

        activeViewCache = new ChatActiveViewCache(Duration.ofSeconds(90), Ticker.systemTicker(), fixedClock);
        scheduler = new ChatEmailDigestScheduler(
                stateRepository,
                chatMessageRepository,
                userRepository,
                preferenceService,
                activeViewCache,
                eventPublisher,
                properties
        );
        ReflectionTestUtils.setField(scheduler, "clock", fixedClock);

        composer = new ChatNotificationComposer(
                properties,
                chatRepository,
                chatMessageRepository,
                userRepository,
                companyRepository,
                orderRepository,
                fileAssetRepository
        );
        SpringTemplateEngine engine = new EmailTemplateConfig().emailTemplateEngine();
        renderer = new EmailTemplateRenderer(engine, properties);

        chatId = UUID.randomUUID();
        buyer = new User();
        buyer.setId(10L);
        buyer.setGuid("buyer-guid");
        buyer.setFirstName("Marina");
        buyer.setLastName("Silva");
        buyer.setEmail("marina@example.com");
        buyer.setStatus(StatusCd.ACTIVE);

        makerUser = new User();
        makerUser.setId(20L);
        makerUser.setGuid("maker-user-guid");
        makerUser.setFirstName("Lucas");
        makerUser.setLastName("Maker");
        makerUser.setStatus(StatusCd.ACTIVE);
    }

    @Test
    void suppressesDigestWhenUserAlreadyReadMessagesInsideWindow() {
        ChatParticipantState state = pendingState(chatId, "buyer-guid", now.minus(Duration.ofMinutes(6)), now.minusSeconds(10), 2);
        state.setLastReadAt(now.minus(Duration.ofMinutes(2)));

        when(stateRepository.findDuePendingStatesForUpdate(now, 50)).thenReturn(List.of(state));
        when(userRepository.findUserByGuid("buyer-guid")).thenReturn(Optional.of(buyer));
        when(preferenceService.isChatDigestEnabled(10L)).thenReturn(true);
        when(chatMessageRepository.findUnreadSince(chatId, "buyer-guid", state.getLastReadAt())).thenReturn(List.of());

        scheduler.processPendingDigests();

        verify(stateRepository).save(state);
        assertNull(state.getPendingSince());
        assertNull(state.getNextNotifyAt());
        assertEquals(0, state.getPendingCount());
        assertNull(state.getLastEmailSentAt());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void defersDigestWhileUserActivelyViewsChatWithinMaxDeferralCap() {
        ChatParticipantState state = pendingState(chatId, "buyer-guid", now.minus(Duration.ofMinutes(5)), now.minusSeconds(5), 3);
        state.setLastReadAt(now.minus(Duration.ofMinutes(8)));
        activeViewCache.markViewing("buyer-guid", chatId);

        when(stateRepository.findDuePendingStatesForUpdate(now, 50)).thenReturn(List.of(state));
        when(userRepository.findUserByGuid("buyer-guid")).thenReturn(Optional.of(buyer));
        when(preferenceService.isChatDigestEnabled(10L)).thenReturn(true);
        when(chatMessageRepository.findUnreadSince(chatId, "buyer-guid", state.getLastReadAt()))
                .thenReturn(List.of(textMessage("maker-user-guid", "Oi Marina", now.minus(Duration.ofMinutes(4)))));

        scheduler.processPendingDigests();

        verify(stateRepository).save(state);
        assertEquals(now.plus(Duration.ofMinutes(2)), state.getNextNotifyAt());
        assertEquals(3, state.getPendingCount());
        assertNull(state.getLastEmailSentAt());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void sendsDigestWhenActivelyViewingExceedsMaxDeferralCap() {
        ChatParticipantState state = pendingState(chatId, "buyer-guid", now.minus(Duration.ofMinutes(16)), now.minusSeconds(5), 2);
        state.setLastReadAt(now.minus(Duration.ofMinutes(20)));
        activeViewCache.markViewing("buyer-guid", chatId);

        when(stateRepository.findDuePendingStatesForUpdate(now, 50)).thenReturn(List.of(state));
        when(userRepository.findUserByGuid("buyer-guid")).thenReturn(Optional.of(buyer));
        when(preferenceService.isChatDigestEnabled(10L)).thenReturn(true);
        when(chatMessageRepository.findUnreadSince(chatId, "buyer-guid", state.getLastReadAt()))
                .thenReturn(List.of(textMessage("maker-user-guid", "Ainda por aí?", now.minus(Duration.ofMinutes(15)))));

        scheduler.processPendingDigests();

        verify(stateRepository).save(state);
        assertEquals(now, state.getLastEmailSentAt());
        assertNull(state.getNextNotifyAt());
        verify(eventPublisher).publishEvent(any(ChatDigestReadyEvent.class));
    }

    @Test
    void clearsPendingAndSkipsEmailWhenChatDigestPreferenceDisabled() {
        ChatParticipantState state = pendingState(chatId, "buyer-guid", now.minus(Duration.ofMinutes(6)), now.minusSeconds(5), 4);
        when(stateRepository.findDuePendingStatesForUpdate(now, 50)).thenReturn(List.of(state));
        when(userRepository.findUserByGuid("buyer-guid")).thenReturn(Optional.of(buyer));
        when(preferenceService.isChatDigestEnabled(10L)).thenReturn(false);

        scheduler.processPendingDigests();

        verify(stateRepository).save(state);
        assertNull(state.getNextNotifyAt());
        assertEquals(0, state.getPendingCount());
        verify(chatMessageRepository, never()).findUnreadSince(any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void consolidatesMultipleUnreadMessagesAndOfferIntoSingleDigestEmailAndRendersTemplate() {
        Instant lastEmailSent = now.minus(Duration.ofMinutes(20));
        ChatParticipantState state = pendingState(chatId, "buyer-guid", now.minus(Duration.ofMinutes(5)), now.minusSeconds(5), 4);
        state.setLastEmailSentAt(lastEmailSent);
        state.setLastReadAt(now.minus(Duration.ofMinutes(25)));

        List<ChatMessage> unread = List.of(
                textMessage("maker-user-guid", "Recebi seu arquivo STL!", now.minus(Duration.ofMinutes(5))),
                textMessage("maker-user-guid", "Vou imprimir em PETG preto fosco.", now.minus(Duration.ofMinutes(4))),
                offerMessage("maker-user-guid", "240.00", now.minus(Duration.ofMinutes(3))),
                textMessage("maker-user-guid", "Prazo de 4 dias úteis após aprovação.", now.minus(Duration.ofMinutes(2)))
        );

        when(stateRepository.findDuePendingStatesForUpdate(now, 50)).thenReturn(List.of(state));
        when(userRepository.findUserByGuid("buyer-guid")).thenReturn(Optional.of(buyer));
        when(preferenceService.isChatDigestEnabled(10L)).thenReturn(true);
        when(chatMessageRepository.findUnreadSince(chatId, "buyer-guid", lastEmailSent)).thenReturn(unread);

        scheduler.processPendingDigests();

        ArgumentCaptor<ChatDigestReadyEvent> eventCaptor = ArgumentCaptor.forClass(ChatDigestReadyEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        ChatDigestReadyEvent publishedEvent = eventCaptor.getValue();
        assertEquals(lastEmailSent, publishedEvent.windowCutoff());

        Chat chat = new Chat();
        chat.setChatId(chatId);
        chat.setStatus(StatusCd.ACTIVE);
        chat.setClientGuid("buyer-guid");
        chat.setCompanyGuid("company-guid");
        chat.setOrderGuid("order-guid-123");

        Company company = new Company();
        company.setGuid("company-guid");
        company.setName("Oficina 3D Sul");

        Order order = new Order();
        order.setGuid("order-guid-123");
        order.setOrderCode("PED-123ABC");
        order.setFileAssetId(77L);

        FileAsset asset = new FileAsset();
        asset.setId(77L);
        asset.setName("suporte-camera.stl");

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(companyRepository.findByGuid("company-guid")).thenReturn(Optional.of(company));
        when(orderRepository.findOrderByGuid("order-guid-123")).thenReturn(Optional.of(order));
        when(fileAssetRepository.findById(77L)).thenReturn(Optional.of(asset));
        when(userRepository.findUserByGuid("maker-user-guid")).thenReturn(Optional.of(makerUser));
        when(chatMessageRepository.findUnreadSince(eq(chatId), eq("buyer-guid"), eq(lastEmailSent))).thenReturn(unread);

        List<EmailQueueMessage> emails = composer.buildChatDigestEmail(publishedEvent);
        assertEquals(1, emails.size());
        EmailQueueMessage msg = emails.get(0);
        assertEquals(EmailCategory.CHAT_DIGEST, msg.category());
        assertEquals("chat/unread-digest", msg.templateName());
        assertEquals(4, msg.variables().get("unreadCount"));
        assertEquals(true, msg.variables().get("hasOffer"));
        assertEquals(true, msg.variables().get("hasMoreMessages"));
        assertEquals(1, msg.variables().get("hiddenCount"));
        assertTrue(String.valueOf(msg.variables().get("latestOfferFormatted")).contains("240,00"));

        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(msg.templateName(), msg.variables());
        assertTrue(rendered.htmlBody().contains("Oficina 3D Sul"));
        assertTrue(rendered.htmlBody().contains("240,00"));
        assertTrue(rendered.htmlBody().contains("PED-123ABC"));
        assertTrue(rendered.htmlBody().contains("https://imprimeai.shop/chat/" + chatId));
        assertTrue(rendered.htmlBody().contains("suporte-camera.stl"));
    }

    private static ChatParticipantState pendingState(
            UUID chatId,
            String userGuid,
            Instant pendingSince,
            Instant nextNotifyAt,
            int pendingCount) {
        ChatParticipantState state = new ChatParticipantState();
        state.setId(new ChatParticipantStateId(chatId, userGuid));
        state.setPendingSince(pendingSince);
        state.setNextNotifyAt(nextNotifyAt);
        state.setPendingCount(pendingCount);
        return state;
    }

    private static ChatMessage textMessage(String senderGuid, String content, Instant createdAt) {
        ChatMessage m = new ChatMessage();
        m.setSenderId(senderGuid);
        m.setType(MessageType.MESSAGE);
        m.setContent(content);
        m.setCreateDt(createdAt);
        return m;
    }

    private static ChatMessage offerMessage(String senderGuid, String amount, Instant createdAt) {
        ChatMessage m = new ChatMessage();
        m.setSenderId(senderGuid);
        m.setType(MessageType.OFFER);
        m.setContent(amount);
        m.setCreateDt(createdAt);
        return m;
    }
}
