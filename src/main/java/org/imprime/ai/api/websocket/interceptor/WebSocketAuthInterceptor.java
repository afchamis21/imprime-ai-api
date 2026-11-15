package org.imprime.ai.api.websocket.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.ws.rs.core.HttpHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.service.AuthTokenService;
import org.imprime.ai.api.service.UserService;
import org.imprime.ai.api.websocket.AuthenticatedUserPrincipal;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService authTokenService;
    private final UserService userService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // Authenticate when establishing the STOMP session.
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new UnauthorizedException();
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();

        if (token.isBlank()) {
            throw new UnauthorizedException();
        }

        DecodedJWT jwt = authTokenService.validateToken(token, AuthToken.TokenType.ACCESS);

        String userGuid = jwt.getSubject();

        User user = userService.findUserByGuid(userGuid).orElseThrow(UnauthorizedException::new);

        if (!StatusCd.ACTIVE.equals(user.getStatus())) {
            throw new UnauthorizedException();
        }

        accessor.setUser(new AuthenticatedUserPrincipal(user));
    }


}
