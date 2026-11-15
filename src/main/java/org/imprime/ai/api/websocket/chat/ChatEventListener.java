package org.imprime.ai.api.websocket.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatEventListener {
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChatEvent(ChatEventToPublish notification) {
        for (String userId : notification.recipients()) {
            messagingTemplate.convertAndSendToUser(
                    userId,
                    "/queue/chat-events",
                    notification.event()
            );
        }
    }
}
