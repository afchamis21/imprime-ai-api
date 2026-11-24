package org.imprime.ai.api.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.imprime.ai.api.config.AuthConfig;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.repo.db.AuthTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    private AuthConfig authConfig;
    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        authConfig = new AuthConfig();

        AuthConfig.TokenConfig accessCfg = new AuthConfig.TokenConfig();
        accessCfg.setDuration(Duration.ofMinutes(5));
        accessCfg.setKey("access-secret-key-for-unit-tests-only-123456");
        accessCfg.setTokenType(AuthToken.TokenType.ACCESS);

        AuthConfig.TokenConfig refreshCfg = new AuthConfig.TokenConfig();
        refreshCfg.setDuration(Duration.ofHours(2));
        refreshCfg.setKey("refresh-secret-key-for-unit-tests-only-654321");
        refreshCfg.setTokenType(AuthToken.TokenType.REFRESH);

        authConfig.setAccessToken(accessCfg);
        authConfig.setRefreshToken(refreshCfg);

        authTokenService = new AuthTokenService(authTokenRepository, authConfig);
        ReflectionTestUtils.setField(authTokenService, "appName", "Imprime Ai");
    }

    @Test
    void generateTokenProducesUniqueJtiAndHashPerInvocationEvenWithinSameSecond() {
        User user = new User();
        user.setId(42L);
        user.setGuid("user-guid-42");
        user.setRole(UserRole.MAKER);
        user.setStatus(StatusCd.ACTIVE);

        AuthToken first = authTokenService.generateToken(user, AuthToken.TokenType.REFRESH);
        AuthToken second = authTokenService.generateToken(user, AuthToken.TokenType.REFRESH);

        assertNotNull(first.getToken());
        assertNotNull(second.getToken());
        assertNotEquals(first.getToken(), second.getToken());
        assertNotEquals(first.getTokenHash(), second.getTokenHash());

        DecodedJWT decoded = authTokenService.validateToken(first.getToken(), AuthToken.TokenType.REFRESH);
        assertEquals("Imprime Ai", decoded.getIssuer());
        assertEquals("user-guid-42", decoded.getSubject());
        assertNotNull(decoded.getId());
    }

    @Test
    void validateTokenRejectsBlankOrMismatchedSigningKey() {
        User user = new User();
        user.setId(43L);
        user.setGuid("user-guid-43");
        user.setRole(UserRole.BUYER);
        user.setStatus(StatusCd.ACTIVE);

        AuthToken accessToken = authTokenService.generateToken(user, AuthToken.TokenType.ACCESS);

        assertThrows(UnauthorizedException.class,
                () -> authTokenService.validateToken("  ", AuthToken.TokenType.REFRESH));
        assertThrows(UnauthorizedException.class,
                () -> authTokenService.validateToken(accessToken.getToken(), AuthToken.TokenType.REFRESH));
    }
}
