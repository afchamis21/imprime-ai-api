package org.imprime.ai.api.notification;

import com.github.benmanes.caffeine.cache.Ticker;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.notification.chat.ChatActiveViewCache;
import org.imprime.ai.api.notification.chat.ChatParticipantState;
import org.imprime.ai.api.notification.chat.ChatParticipantStateId;
import org.imprime.ai.api.notification.chat.ChatParticipantStateRepository;
import org.imprime.ai.api.notification.chat.ChatReadStateService;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatReadStateServiceTest {

    @Mock
    private ChatParticipantStateRepository stateRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ChatActiveViewCache activeViewCache;
    private NotificationProperties properties;
    private ChatReadStateService service;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-27T12:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);

        properties = new NotificationProperties();
        properties.getChat().setDebounceWindow(Duration.ofMinutes(5));
        properties.getChat().setOfferDebounceWindow(Duration.ofMinutes(2));
        properties.getChat().setCooldownWindow(Duration.ofMinutes(15));
        properties.getChat().setActiveViewTtl(Duration.ofSeconds(90));

        activeViewCache = new ChatActiveViewCache(Duration.ofSeconds(90), Ticker.systemTicker(), fixedClock);
        service = new ChatReadStateService(stateRepository, chatMessageRepository, activeViewCache, properties);
        // Set fixed clock reflectively via package-private/ReflectionTestUtils
        org.springframework.test.util.ReflectionTestUtils.setField(service, "clock", fixedClock);
    }

    @Test
    void recordIncomingMessageIgnoresSystemMessages() {
        UUID chatId = UUID.randomUUID();
        service.recordIncomingMessage(chatId, "buyer-1", MessageType.SYSTEM, List.of("buyer-1", "maker-1"));

        verifyNoInteractions(stateRepository);
        assertFalse(activeViewCache.isViewing("buyer-1", chatId));
    }

    @Test
    void recordIncomingMessageMarksSenderReadAndSchedulesRecipientsWithDebounceAndCooldown() {
        UUID chatId = UUID.randomUUID();
        service.recordIncomingMessage(
                chatId,
                "buyer-1",
                MessageType.MESSAGE,
                List.of("buyer-1", "maker-1", "maker-2", "maker-1")
        );

        verify(stateRepository).upsertMarkAsRead(chatId, "buyer-1", now);
        assertTrue(activeViewCache.isViewing("buyer-1", chatId));

        Instant expectedDebounce = now.plus(Duration.ofMinutes(5));
        verify(stateRepository).upsertSchedulePendingDigest(chatId, "maker-1", now, expectedDebounce, 900.0d);
        verify(stateRepository).upsertSchedulePendingDigest(chatId, "maker-2", now, expectedDebounce, 900.0d);
        verifyNoMoreInteractions(stateRepository);
    }

    @Test
    void recordIncomingOfferUsesShorterOfferDebounceWindow() {
        UUID chatId = UUID.randomUUID();
        service.recordIncomingMessage(chatId, "maker-1", MessageType.OFFER, List.of("buyer-1", "maker-1"));

        verify(stateRepository).upsertMarkAsRead(chatId, "maker-1", now);
        Instant expectedOfferNotifyAt = now.plus(Duration.ofMinutes(2));
        verify(stateRepository).upsertSchedulePendingDigest(chatId, "buyer-1", now, expectedOfferNotifyAt, 900.0d);
    }

    @Test
    void markAsReadUpdatesPersistentCursorAndActiveViewCache() {
        UUID chatId = UUID.randomUUID();
        service.markAsRead(chatId, "buyer-1");

        verify(stateRepository).upsertMarkAsRead(chatId, "buyer-1", now);
        assertTrue(activeViewCache.isViewing("buyer-1", chatId));
    }

    @Test
    void countUnreadUsesParticipantCursorOrEpochFallback() {
        UUID chatId = UUID.randomUUID();
        Instant lastRead = Instant.parse("2026-11-27T11:40:00Z");
        ChatParticipantState state = new ChatParticipantState();
        state.setId(new ChatParticipantStateId(chatId, "buyer-1"));
        state.setLastReadAt(lastRead);

        when(stateRepository.findById(new ChatParticipantStateId(chatId, "buyer-1"))).thenReturn(Optional.of(state));
        when(chatMessageRepository.countUnreadSince(chatId, "buyer-1", lastRead)).thenReturn(3L);

        assertEquals(3, service.countUnread(chatId, "buyer-1"));

        UUID freshChatId = UUID.randomUUID();
        when(stateRepository.findById(new ChatParticipantStateId(freshChatId, "buyer-1"))).thenReturn(Optional.empty());
        when(chatMessageRepository.countUnreadSince(freshChatId, "buyer-1", Instant.EPOCH)).thenReturn(7L);

        assertEquals(7, service.countUnread(freshChatId, "buyer-1"));
    }

    @Test
    void countUnreadByChatIdsMapsUuidAndStringRows() {
        UUID chatA = UUID.randomUUID();
        UUID chatB = UUID.randomUUID();
        when(chatMessageRepository.countUnreadBatch(List.of(chatA, chatB), "buyer-1")).thenReturn(List.of(
                new Object[]{chatA, 2L},
                new Object[]{chatB.toString(), 5}
        ));

        Map<UUID, Integer> result = service.countUnreadByChatIds(List.of(chatA, chatB), "buyer-1");
        assertEquals(Map.of(chatA, 2, chatB, 5), result);
    }
}
