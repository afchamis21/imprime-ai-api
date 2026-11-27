package org.imprime.ai.api.notification.chat;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.ChatMessage;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.ChatDigestReadyEvent;
import org.imprime.ai.api.notification.preferences.NotificationPreferenceService;
import org.imprime.ai.api.repo.db.ChatMessageRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Agendador responsável por consolidar mensagens de chat pendentes em um único e-mail resumo,
 * evitando spam quando há trocas rápidas ou quando o usuário já leu ou está visualizando o chat.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEmailDigestScheduler {
    private static final Duration ACTIVE_VIEW_DEFER_STEP = Duration.ofMinutes(2);

    private final ChatParticipantStateRepository stateRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NotificationPreferenceService preferenceService;
    private final ChatActiveViewCache activeViewCache;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationProperties properties;
    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Scheduled(
            fixedDelayString = "${imprime.ai.notification.chat.poll-interval:30000}",
            initialDelayString = "${imprime.ai.notification.chat.poll-interval:30000}"
    )
    @Transactional
    public void processPendingDigests() {
        if (!properties.isEnabled()) {
            return;
        }

        Instant now = clock.instant();
        int batchSize = Math.max(1, properties.getChat().getBatchSize());
        List<ChatParticipantState> dueStates = stateRepository.findDuePendingStatesForUpdate(now, batchSize);
        if (dueStates == null || dueStates.isEmpty()) {
            return;
        }

        Duration maxDeferral = properties.getChat().getMaxActiveDeferral() != null
                ? properties.getChat().getMaxActiveDeferral()
                : Duration.ofMinutes(15);

        for (ChatParticipantState state : dueStates) {
            try {
                processSingleState(state, now, maxDeferral);
            } catch (RuntimeException ex) {
                log.warn("Falha ao processar digest de chat (chatId={}, userGuid={}): {}",
                        state.getId() != null ? state.getId().getChatId() : null,
                        state.getId() != null ? state.getId().getUserGuid() : null,
                        ex.getMessage());
            }
        }
    }

    private void processSingleState(ChatParticipantState state, Instant now, Duration maxDeferral) {
        UUID chatId = state.getId().getChatId();
        String userGuid = state.getId().getUserGuid();

        User recipient = userRepository.findUserByGuid(userGuid).orElse(null);
        if (recipient == null
                || !StatusCd.ACTIVE.equals(recipient.getStatus())
                || recipient.getEmail() == null
                || recipient.getEmail().isBlank()
                || !preferenceService.isChatDigestEnabled(recipient.getId())) {
            clearPending(state, now);
            stateRepository.save(state);
            return;
        }

        Instant cutoff = maxInstant(state.getLastReadAt(), state.getLastEmailSentAt());
        List<ChatMessage> unreadMessages = chatMessageRepository.findUnreadSince(chatId, userGuid, cutoff);
        if (unreadMessages == null || unreadMessages.isEmpty()) {
            clearPending(state, now);
            stateRepository.save(state);
            return;
        }

        boolean viewingNow = activeViewCache.isViewing(userGuid, chatId);
        Instant pendingSince = state.getPendingSince() != null ? state.getPendingSince() : now;
        if (viewingNow && Duration.between(pendingSince, now).compareTo(maxDeferral) < 0) {
            state.setNextNotifyAt(now.plus(ACTIVE_VIEW_DEFER_STEP));
            state.setUpdatedAt(now);
            stateRepository.save(state);
            return;
        }

        clearPending(state, now);
        state.setLastEmailSentAt(now);
        stateRepository.save(state);

        eventPublisher.publishEvent(new ChatDigestReadyEvent(chatId, userGuid, cutoff, now));
    }

    private static void clearPending(ChatParticipantState state, Instant now) {
        state.setPendingSince(null);
        state.setNextNotifyAt(null);
        state.setPendingCount(0);
        state.setUpdatedAt(now);
    }

    private static Instant maxInstant(Instant a, Instant b) {
        if (a == null && b == null) {
            return Instant.EPOCH;
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }
}
