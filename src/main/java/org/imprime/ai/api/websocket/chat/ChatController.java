package org.imprime.ai.api.websocket.chat;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.chat.SendMessageRequest;
import org.imprime.ai.api.http.request.chat.StartChatRequest;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.service.ChatService;
import org.imprime.ai.api.websocket.AuthenticatedUserPrincipal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @MessageMapping("chats/start")
    public void startChat(
            StartChatRequest request,
            Principal principal
    ) {
        try {
            var ctx = ServiceContext.getContext();
            if (!(principal instanceof AuthenticatedUserPrincipal(User user)) || user == null) {
                throw new UnauthorizedException();
            }

            ctx.setUser(user);

            chatService.start(request);
        } finally {
            ServiceContext.clear();
        }
    }

    @MessageMapping("chats/{chatId}/messages")
    public void sendMessage(
            @DestinationVariable UUID chatId,
            SendMessageRequest request,
            Principal principal
    ) {
        try {
            var ctx = ServiceContext.getContext();
            if (!(principal instanceof AuthenticatedUserPrincipal(User user)) || user == null) {
                throw new UnauthorizedException();
            }

            ctx.setUser(user);

            chatService.sendMessage(chatId, request);
        } finally {
            ServiceContext.clear();
        }
    }
}
