package org.imprime.ai.api.websocket.chat;

import java.util.List;

public record ChatEventToPublish(
        List<String> recipients, ChatEvent<?> event
) {
}
