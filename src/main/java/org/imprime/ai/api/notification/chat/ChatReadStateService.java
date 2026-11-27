package org.imprime.ai.api.notification.chat;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.enums.MessageType;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.ChatMessageRecordedEvent;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Gerencia o cursor de leitura persistente por participante do chat e o agendamento anti-spam de e-mails.
 */
@Service
@RequiredArgsConstructor
public class ChatReadStateService {
    private final ChatParticipantStateRepository stateRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatActiveViewCache activeViewCache;
    private final NotificationProperties properties;
    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @EventListener
    @Transactional
    public void onChatMessageRecorded(ChatMessageRecordedEvent event) {
        if (event == null) {
            return;
        }
        recordIncomingMessage(
                event.chatId(),
                event.senderGuid(),
                event.messageType(),
                event.recipientGuids()
        );
    }

    @Transactional
    public void markAsRead(UUID chatId, String userGuid) {
        if (chatId == null || userGuid == null || userGuid.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        stateRepository.upsertMarkAsRead(chatId, userGuid, now);
        activeViewCache.markViewing(userGuid, chatId);
    }

    @Transactional
    public void recordIncomingMessage(
            UUID chatId,
            String senderGuid,
            MessageType messageType,
            List<String> memberGuids) {
        if (chatId == null || MessageType.SYSTEM.equals(messageType)) {
            return;
        }

        Instant now = clock.instant();
        if (senderGuid != null && !senderGuid.isBlank()) {
            stateRepository.upsertMarkAsRead(chatId, senderGuid, now);
            activeViewCache.markViewing(senderGuid, chatId);
        }

        if (memberGuids == null || memberGuids.isEmpty()) {
            return;
        }

        Duration debounce = MessageType.OFFER.equals(messageType)
                ? properties.getChat().getOfferDebounceWindow()
                : properties.getChat().getDebounceWindow();
        Duration cooldown = properties.getChat().getCooldownWindow();
        Instant candidateNotifyAt = now.plus(debounce != null ? debounce : Duration.ofMinutes(5));
        double cooldownSeconds = cooldown != null ? Math.max(0L, cooldown.toSeconds()) : 900d;

        Set<String> uniqueRecipients = new LinkedHashSet<>();
        for (String guid : memberGuids) {
            if (guid != null && !guid.isBlank() && !Objects.equals(guid, senderGuid)) {
                uniqueRecipients.add(guid);
            }
        }

        for (String recipientGuid : uniqueRecipients) {
            stateRepository.upsertSchedulePendingDigest(chatId, recipientGuid, now, candidateNotifyAt, cooldownSeconds);
        }
    }

    public int countUnread(UUID chatId, String userGuid) {
        if (chatId == null || userGuid == null || userGuid.isBlank()) {
            return 0;
        }
        Instant lastReadAt = stateRepository.findById(new ChatParticipantStateId(chatId, userGuid))
                .map(ChatParticipantState::getLastReadAt)
                .orElse(Instant.EPOCH);
        Instant effectiveSince = lastReadAt != null ? lastReadAt : Instant.EPOCH;
        long count = chatMessageRepository.countUnreadSince(chatId, userGuid, effectiveSince);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, count));
    }

    public Map<UUID, Integer> countUnreadByChatIds(Collection<UUID> chatIds, String userGuid) {
        if (chatIds == null || chatIds.isEmpty() || userGuid == null || userGuid.isBlank()) {
            return Map.of();
        }
        List<Object[]> rows = chatMessageRepository.countUnreadBatch(chatIds, userGuid);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                continue;
            }
            UUID chatId = row[0] instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row[0]));
            int count = ((Number) row[1]).intValue();
            result.put(chatId, Math.max(0, count));
        }
        return result;
    }
}
