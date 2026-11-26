package org.imprime.ai.api.websocket.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.ws.rs.core.HttpHeaders;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.cache.MakerPresenceCache;
import org.imprime.ai.api.service.AuthTokenService;
import org.imprime.ai.api.service.UserService;
import org.imprime.ai.api.websocket.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private UserService userService;
    @Mock
    private MessageChannel channel;

    private MakerPresenceCache makerPresenceCache;
    private WebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        makerPresenceCache = new MakerPresenceCache(Duration.ofMinutes(4), 1_000L);
        interceptor = new WebSocketAuthInterceptor(authTokenService, userService, makerPresenceCache);
    }

    @Test
    void connectAuthenticatesAndMarksMakerPresenceOnline() {
        DecodedJWT jwt = mock(DecodedJWT.class);
        when(jwt.getSubject()).thenReturn("maker-guid-1");
        when(authTokenService.validateToken("access-jwt", AuthToken.TokenType.ACCESS)).thenReturn(jwt);

        User maker = new User();
        maker.setId(10L);
        maker.setGuid("maker-guid-1");
        maker.setCompanyId(200L);
        maker.setStatus(StatusCd.ACTIVE);
        when(userService.findUserByGuid("maker-guid-1")).thenReturn(Optional.of(maker));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer access-jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertSame(message, result);
        assertInstanceOf(AuthenticatedUserPrincipal.class, accessor.getUser());
        assertTrue(makerPresenceCache.isOnline(200L));
    }

    @Test
    void sendRefreshesMakerPresenceWhenAuthenticatedPrincipalPresent() {
        User maker = new User();
        maker.setId(11L);
        maker.setGuid("maker-guid-2");
        maker.setCompanyId(201L);
        maker.setStatus(StatusCd.ACTIVE);

        assertFalse(makerPresenceCache.isOnline(201L));
        when(userService.isActiveByGuid(maker.getGuid())).thenReturn(true);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setUser(new AuthenticatedUserPrincipal(maker));
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, channel);

        assertTrue(makerPresenceCache.isOnline(201L));
        verifyNoInteractions(authTokenService);
        verify(userService).isActiveByGuid(maker.getGuid());
    }
}
