package org.imprime.ai.api.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.imprime.ai.api.config.AuthConfig;
import org.imprime.ai.api.http.request.auth.LoginRequest;
import org.imprime.ai.api.http.request.auth.RefreshTokenRequest;
import org.imprime.ai.api.http.response.AuthResponse;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.repo.cache.MakerPresenceCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    private MakerPresenceCache makerPresenceCache;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthConfig authConfig = new AuthConfig();
        AuthConfig.TokenConfig accessCfg = new AuthConfig.TokenConfig();
        accessCfg.setDuration(Duration.ofMinutes(5));
        accessCfg.setTokenType(AuthToken.TokenType.ACCESS);
        AuthConfig.TokenConfig refreshCfg = new AuthConfig.TokenConfig();
        refreshCfg.setDuration(Duration.ofHours(2));
        refreshCfg.setTokenType(AuthToken.TokenType.REFRESH);
        authConfig.setAccessToken(accessCfg);
        authConfig.setRefreshToken(refreshCfg);

        makerPresenceCache = new MakerPresenceCache(Duration.ofMinutes(4), 1_000L);
        authService = new AuthService(authConfig, userService, authTokenService, passwordEncoder, makerPresenceCache);
    }

    @Test
    void refreshPreservesRawJwtWhenNotRotatedEvenThoughJpaEntityTokenIsTransientNull() {
        User user = buildActiveUser(10L, "user-guid-10");

        DecodedJWT jwt = mock(DecodedJWT.class);
        when(jwt.getSubject()).thenReturn("user-guid-10");
        when(jwt.getToken()).thenReturn("incoming-refresh-jwt");
        when(authTokenService.validateToken("incoming-refresh-jwt", AuthToken.TokenType.REFRESH))
                .thenReturn(jwt);

        // Simula entidade carregada do PostgreSQL (@Transient token = null), com 115 min restantes (> 10 min threshold)
        AuthToken persistedRefresh = new AuthToken();
        persistedRefresh.setId(77L);
        persistedRefresh.setUserId(10L);
        persistedRefresh.setType(AuthToken.TokenType.REFRESH);
        persistedRefresh.setRevoked(false);
        persistedRefresh.setExpiresAt(Instant.now().plus(Duration.ofMinutes(115)));
        persistedRefresh.setToken(null);

        when(authTokenService.findByToken("incoming-refresh-jwt")).thenReturn(Optional.of(persistedRefresh));
        when(userService.findUserById(10L)).thenReturn(Optional.of(user));

        AuthToken newAccess = new AuthToken();
        newAccess.setType(AuthToken.TokenType.ACCESS);
        newAccess.setToken("fresh-access-jwt");
        newAccess.setExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        when(authTokenService.generateToken(user, AuthToken.TokenType.ACCESS)).thenReturn(newAccess);

        AuthResponse response = authService.refresh(new RefreshTokenRequest("incoming-refresh-jwt"));

        assertNotNull(response);
        assertEquals("fresh-access-jwt", response.accessToken().token());
        assertNotNull(response.refreshToken().token());
        assertEquals("incoming-refresh-jwt", response.refreshToken().token());
        assertEquals(persistedRefresh.getExpiresAt(), response.refreshToken().expiresAt());
        verify(authTokenService, never()).revoke(any());
        verify(authTokenService, never()).persist(any());
    }

    @Test
    void refreshRotatesRefreshTokenWhenRemainingLifetimeIsWithinTwiceAccessDuration() {
        User user = buildActiveUser(11L, "user-guid-11");

        DecodedJWT jwt = mock(DecodedJWT.class);
        when(jwt.getSubject()).thenReturn("user-guid-11");
        when(jwt.getToken()).thenReturn("expiring-refresh-jwt");
        when(authTokenService.validateToken("expiring-refresh-jwt", AuthToken.TokenType.REFRESH))
                .thenReturn(jwt);

        // Restando 8 minutos (<= 2 * 5m = 10m): deve rotacionar
        AuthToken expiringRefresh = new AuthToken();
        expiringRefresh.setId(88L);
        expiringRefresh.setUserId(11L);
        expiringRefresh.setType(AuthToken.TokenType.REFRESH);
        expiringRefresh.setRevoked(false);
        expiringRefresh.setExpiresAt(Instant.now().plus(Duration.ofMinutes(8)));
        expiringRefresh.setToken(null);

        when(authTokenService.findByToken("expiring-refresh-jwt")).thenReturn(Optional.of(expiringRefresh));
        when(userService.findUserById(11L)).thenReturn(Optional.of(user));

        AuthToken newAccess = new AuthToken();
        newAccess.setType(AuthToken.TokenType.ACCESS);
        newAccess.setToken("rotated-access-jwt");
        newAccess.setExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));

        AuthToken rotatedRefresh = new AuthToken();
        rotatedRefresh.setType(AuthToken.TokenType.REFRESH);
        rotatedRefresh.setToken("rotated-refresh-jwt");
        rotatedRefresh.setExpiresAt(Instant.now().plus(Duration.ofHours(2)));

        when(authTokenService.generateToken(user, AuthToken.TokenType.ACCESS)).thenReturn(newAccess);
        when(authTokenService.generateToken(user, AuthToken.TokenType.REFRESH)).thenReturn(rotatedRefresh);

        AuthResponse response = authService.refresh(new RefreshTokenRequest("expiring-refresh-jwt"));

        assertEquals("rotated-access-jwt", response.accessToken().token());
        assertEquals("rotated-refresh-jwt", response.refreshToken().token());
        verify(authTokenService).revoke(expiringRefresh);
        verify(authTokenService).persist(rotatedRefresh);
    }

    @Test
    void refreshThrowsUnauthorizedForNullBlankRevokedOrExpiredTokens() {
        assertThrows(UnauthorizedException.class, () -> authService.refresh(null));
        assertThrows(UnauthorizedException.class, () -> authService.refresh(new RefreshTokenRequest(null)));
        assertThrows(UnauthorizedException.class, () -> authService.refresh(new RefreshTokenRequest("   ")));

        DecodedJWT jwt = mock(DecodedJWT.class);
        when(jwt.getSubject()).thenReturn("user-guid-12");
        when(jwt.getToken()).thenReturn("bad-refresh-jwt");
        when(authTokenService.validateToken("bad-refresh-jwt", AuthToken.TokenType.REFRESH)).thenReturn(jwt);

        AuthToken revoked = new AuthToken();
        revoked.setUserId(12L);
        revoked.setRevoked(true);
        revoked.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        when(authTokenService.findByToken("bad-refresh-jwt")).thenReturn(Optional.of(revoked));

        assertThrows(UnauthorizedException.class, () -> authService.refresh(new RefreshTokenRequest("bad-refresh-jwt")));

        AuthToken expired = new AuthToken();
        expired.setUserId(12L);
        expired.setRevoked(false);
        expired.setExpiresAt(Instant.now().minus(Duration.ofSeconds(10)));
        when(authTokenService.findByToken("bad-refresh-jwt")).thenReturn(Optional.of(expired));

        assertThrows(UnauthorizedException.class, () -> authService.refresh(new RefreshTokenRequest("bad-refresh-jwt")));
        verifyNoInteractions(userService);
    }

    @Test
    void loginMarksMakerOnlineInPresenceCache() {
        User maker = buildActiveUser(20L, "maker-guid-20");
        maker.setCompanyId(300L);
        maker.setPasswordHash("hashed-pw");

        when(userService.findUserByEmail("maker@imprime.ai")).thenReturn(Optional.of(maker));
        when(passwordEncoder.matches("secret", "hashed-pw")).thenReturn(true);

        AuthToken access = new AuthToken();
        access.setType(AuthToken.TokenType.ACCESS);
        access.setToken("access-jwt");
        access.setExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));

        AuthToken refresh = new AuthToken();
        refresh.setType(AuthToken.TokenType.REFRESH);
        refresh.setToken("refresh-jwt");
        refresh.setExpiresAt(Instant.now().plus(Duration.ofHours(2)));

        when(authTokenService.generateToken(maker, AuthToken.TokenType.ACCESS)).thenReturn(access);
        when(authTokenService.generateToken(maker, AuthToken.TokenType.REFRESH)).thenReturn(refresh);

        AuthResponse response = authService.login(new LoginRequest("maker@imprime.ai", "secret"));

        assertNotNull(response);
        assertTrue(makerPresenceCache.isOnline(300L));
    }

    @Test
    void logoutRevokesRefreshTokenAndMarksMakerOfflineWithValidAccessToken() {
        User maker = buildActiveUser(21L, "maker-guid-21");
        maker.setCompanyId(301L);
        makerPresenceCache.markOnline(301L);

        DecodedJWT accessJwt = mock(DecodedJWT.class);
        when(accessJwt.getSubject()).thenReturn("maker-guid-21");
        when(authTokenService.validateToken("valid-access-jwt", AuthToken.TokenType.ACCESS)).thenReturn(accessJwt);
        when(userService.findUserByGuid("maker-guid-21")).thenReturn(Optional.of(maker));

        AuthToken refreshToken = new AuthToken();
        refreshToken.setId(501L);
        refreshToken.setUserId(21L);
        refreshToken.setRevoked(false);
        when(authTokenService.findByToken("valid-refresh-jwt")).thenReturn(Optional.of(refreshToken));
        when(userService.findUserById(21L)).thenReturn(Optional.of(maker));

        authService.logout("Bearer valid-access-jwt", new RefreshTokenRequest("valid-refresh-jwt"));

        verify(authTokenService).revoke(refreshToken);
        assertFalse(makerPresenceCache.isOnline(301L));
    }

    @Test
    void logoutStillRevokesRefreshTokenAndMarksOfflineWhenAccessTokenExpiredOrMissing() {
        User maker = buildActiveUser(22L, "maker-guid-22");
        maker.setCompanyId(302L);
        makerPresenceCache.markOnline(302L);

        when(authTokenService.validateToken("expired-access-jwt", AuthToken.TokenType.ACCESS))
                .thenThrow(new UnauthorizedException());

        AuthToken refreshToken = new AuthToken();
        refreshToken.setId(502L);
        refreshToken.setUserId(22L);
        refreshToken.setRevoked(false);
        when(authTokenService.findByToken("active-refresh-jwt")).thenReturn(Optional.of(refreshToken));
        when(userService.findUserById(22L)).thenReturn(Optional.of(maker));

        assertDoesNotThrow(() ->
                authService.logout("Bearer expired-access-jwt", new RefreshTokenRequest("active-refresh-jwt")));

        verify(authTokenService).revoke(refreshToken);
        assertFalse(makerPresenceCache.isOnline(302L));

        assertDoesNotThrow(() -> authService.logout(null, null));
    }

    private User buildActiveUser(Long id, String guid) {
        User user = new User();
        user.setId(id);
        user.setGuid(guid);
        user.setFirstName("Maker");
        user.setLastName("Test");
        user.setEmail("maker@imprime.ai");
        user.setRole(UserRole.MAKER);
        user.setStatus(StatusCd.ACTIVE);
        return user;
    }
}
