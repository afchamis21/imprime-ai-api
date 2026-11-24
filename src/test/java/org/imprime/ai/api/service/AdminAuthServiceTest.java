package org.imprime.ai.api.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.imprime.ai.api.config.AuthConfig;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.auth.LoginRequest;
import org.imprime.ai.api.http.request.auth.RefreshTokenRequest;
import org.imprime.ai.api.http.response.AuthResponse;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private AdminAuditService adminAuditService;

    private AuthConfig authConfig;
    private AdminAuthService adminAuthService;

    @BeforeEach
    void setUp() {
        authConfig = new AuthConfig();
        AuthConfig.TokenConfig accessCfg = new AuthConfig.TokenConfig();
        accessCfg.setDuration(Duration.ofMinutes(5));
        accessCfg.setTokenType(AuthToken.TokenType.ACCESS);
        AuthConfig.TokenConfig refreshCfg = new AuthConfig.TokenConfig();
        refreshCfg.setDuration(Duration.ofHours(2));
        refreshCfg.setTokenType(AuthToken.TokenType.REFRESH);
        authConfig.setAccessToken(accessCfg);
        authConfig.setRefreshToken(refreshCfg);

        adminAuthService = new AdminAuthService(
                authConfig,
                userService,
                authTokenService,
                passwordEncoder,
                adminAuditService
        );
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void loginSucceedsForActiveAdminAndRecordsAudit() {
        User admin = buildUser(1L, "admin-guid-1", UserRole.ADMIN, StatusCd.ACTIVE);
        when(userService.findUserByEmail("admin@imprime.ai")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Secret@123", admin.getPasswordHash())).thenReturn(true);

        AuthToken accessToken = buildToken(1L, AuthToken.TokenType.ACCESS, "access-jwt", Instant.now().plusSeconds(300));
        AuthToken refreshToken = buildToken(1L, AuthToken.TokenType.REFRESH, "refresh-jwt", Instant.now().plusSeconds(7200));
        when(authTokenService.generateToken(admin, AuthToken.TokenType.ACCESS)).thenReturn(accessToken);
        when(authTokenService.generateToken(admin, AuthToken.TokenType.REFRESH)).thenReturn(refreshToken);

        AuthResponse response = adminAuthService.login(new LoginRequest("admin@imprime.ai", "Secret@123"));

        assertNotNull(response);
        assertEquals("access-jwt", response.accessToken().token());
        assertEquals("refresh-jwt", response.refreshToken().token());
        assertEquals(UserRole.ADMIN, response.user().role());
        assertEquals(StatusCd.ACTIVE, response.user().status());
        verify(authTokenService).persist(refreshToken);
        verify(adminAuditService).recordForActor(
                eq(admin),
                eq(AuditAction.ADMIN_LOGIN),
                eq("USER"),
                eq("admin-guid-1"),
                anyString(),
                isNull(),
                any()
        );
    }

    @Test
    void loginThrowsForbiddenForBuyerOrMakerUser() {
        User buyer = buildUser(2L, "buyer-guid-2", UserRole.BUYER, StatusCd.ACTIVE);
        when(userService.findUserByEmail("buyer@imprime.ai")).thenReturn(Optional.of(buyer));
        when(passwordEncoder.matches("Secret@123", buyer.getPasswordHash())).thenReturn(true);

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> adminAuthService.login(new LoginRequest("buyer@imprime.ai", "Secret@123"))
        );

        assertEquals(MessageCd.ADMIN_ROLE_REQUIRED, ex.getMessageCd());
        verifyNoInteractions(authTokenService, adminAuditService);
    }

    @Test
    void loginThrowsUnauthorizedForSuspendedAdmin() {
        User suspended = buildUser(3L, "admin-guid-3", UserRole.ADMIN, StatusCd.SUSPENDED);
        when(userService.findUserByEmail("suspended@imprime.ai")).thenReturn(Optional.of(suspended));
        when(passwordEncoder.matches("Secret@123", suspended.getPasswordHash())).thenReturn(true);

        assertThrows(
                UnauthorizedException.class,
                () -> adminAuthService.login(new LoginRequest("suspended@imprime.ai", "Secret@123"))
        );
        verifyNoInteractions(authTokenService, adminAuditService);
    }

    @Test
    void refreshThrowsForbiddenAndRevokesTokensWhenAdminWasDowngraded() {
        DecodedJWT jwt = mock(DecodedJWT.class);
        when(jwt.getSubject()).thenReturn("user-guid-4");
        when(jwt.getToken()).thenReturn("refresh-raw");
        when(authTokenService.validateToken("refresh-raw", AuthToken.TokenType.REFRESH)).thenReturn(jwt);

        AuthToken storedRefresh = buildToken(4L, AuthToken.TokenType.REFRESH, "refresh-raw", Instant.now().plusSeconds(3600));
        when(authTokenService.findByToken("refresh-raw")).thenReturn(Optional.of(storedRefresh));

        User downgraded = buildUser(4L, "user-guid-4", UserRole.BUYER, StatusCd.ACTIVE);
        when(userService.findUserById(4L)).thenReturn(Optional.of(downgraded));

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> adminAuthService.refresh(new RefreshTokenRequest("refresh-raw"))
        );

        assertEquals(MessageCd.ADMIN_ROLE_REQUIRED, ex.getMessageCd());
        verify(authTokenService).revokeTokensForUser(downgraded);
    }

    @Test
    void refreshPreservesRawRefreshTokenWhenNotRotatedAndEntityTokenIsTransientNull() {
        DecodedJWT jwt = mock(DecodedJWT.class);
        when(jwt.getSubject()).thenReturn("admin-guid-6");
        when(jwt.getToken()).thenReturn("refresh-raw");
        when(authTokenService.validateToken("refresh-raw", AuthToken.TokenType.REFRESH)).thenReturn(jwt);

        AuthToken storedRefresh = buildToken(6L, AuthToken.TokenType.REFRESH, null, Instant.now().plusSeconds(6900));
        when(authTokenService.findByToken("refresh-raw")).thenReturn(Optional.of(storedRefresh));

        User admin = buildUser(6L, "admin-guid-6", UserRole.ADMIN, StatusCd.ACTIVE);
        when(userService.findUserById(6L)).thenReturn(Optional.of(admin));

        AuthToken newAccess = buildToken(6L, AuthToken.TokenType.ACCESS, "new-admin-access", Instant.now().plusSeconds(300));
        when(authTokenService.generateToken(admin, AuthToken.TokenType.ACCESS)).thenReturn(newAccess);

        AuthResponse response = adminAuthService.refresh(new RefreshTokenRequest("refresh-raw"));

        assertEquals("new-admin-access", response.accessToken().token());
        assertEquals("refresh-raw", response.refreshToken().token());
        verify(authTokenService, never()).revoke(any());
        verify(authTokenService, never()).persist(any());
    }

    @Test
    void logoutRevokesTokensInvalidatesCacheAndRecordsAudit() {
        User admin = buildUser(5L, "admin-guid-5", UserRole.ADMIN, StatusCd.ACTIVE);
        ServiceContext.getContext().setUser(admin);

        adminAuthService.logout();

        verify(authTokenService).revokeTokensForUser(admin);
        verify(userService).invalidateCache(admin);
        verify(adminAuditService).recordForActor(
                eq(admin),
                eq(AuditAction.ADMIN_LOGOUT),
                eq("USER"),
                eq("admin-guid-5"),
                anyString(),
                isNull(),
                isNull()
        );
    }

    private User buildUser(Long id, String guid, UserRole role, StatusCd status) {
        User user = new User();
        user.setId(id);
        user.setGuid(guid);
        user.setFirstName("Admin");
        user.setLastName("User");
        user.setEmail(guid + "@imprime.ai");
        user.setPasswordHash("hashed");
        user.setPhoneNumber("11999999999");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private AuthToken buildToken(Long userId, AuthToken.TokenType type, String raw, Instant expiresAt) {
        AuthToken token = new AuthToken();
        token.setId(10L);
        token.setUserId(userId);
        token.setType(type);
        token.setToken(raw);
        token.setRevoked(false);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
