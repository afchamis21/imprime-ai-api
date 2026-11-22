package org.imprime.ai.api.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.AuthConfig;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.auth.LoginRequest;
import org.imprime.ai.api.http.request.auth.RefreshTokenRequest;
import org.imprime.ai.api.http.response.AuthResponse;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.TokenDTO;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.AuditAction;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private static final String DUMMY_BCRYPT = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHi8uK2Q9uQ8QYq9Y5W5y5W5y5W5y5W";

    private final AuthConfig authConfig;
    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AdminAuditService adminAuditService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (request == null || request.email() == null || request.email().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new UnauthorizedException();
        }

        Optional<User> optionalUser = userService.findUserByEmail(request.email().trim());
        if (optionalUser.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT);
            throw new UnauthorizedException();
        }

        User user = optionalUser.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException();
        }

        if (!StatusCd.ACTIVE.equals(user.getStatus())) {
            throw new UnauthorizedException();
        }

        if (!user.isAdmin()) {
            log.warn("Non-admin user [{}] with role [{}] attempted admin login", user.getGuid(), user.getRole());
            throw new ForbiddenException(MessageCd.ADMIN_ROLE_REQUIRED);
        }

        AuthResponse response = generateTokens(user, null);
        adminAuditService.recordForActor(
                user,
                AuditAction.ADMIN_LOGIN,
                "USER",
                user.getGuid(),
                "Login administrativo realizado",
                null,
                Map.of("role", user.getRole().name())
        );
        return response;
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new UnauthorizedException();
        }

        DecodedJWT jwt = authTokenService.validateToken(request.token(), AuthToken.TokenType.REFRESH);
        String userGuid = jwt.getSubject();

        AuthToken refreshToken = authTokenService.findByToken(jwt.getToken())
                .orElseThrow(UnauthorizedException::new);

        if (Boolean.TRUE.equals(refreshToken.getRevoked()) || refreshToken.isExpired()) {
            throw new UnauthorizedException();
        }

        User user = userService.findUserById(refreshToken.getUserId())
                .orElseThrow(UnauthorizedException::new);

        if (!Objects.equals(user.getGuid(), userGuid) || !StatusCd.ACTIVE.equals(user.getStatus())) {
            throw new UnauthorizedException();
        }

        if (!user.isAdmin()) {
            log.warn("Non-admin user [{}] attempted admin token refresh; revoking tokens", user.getGuid());
            authTokenService.revokeTokensForUser(user);
            throw new ForbiddenException(MessageCd.ADMIN_ROLE_REQUIRED);
        }

        return generateTokens(user, refreshToken);
    }

    @Transactional
    public void logout() {
        User admin = ServiceContext.getUserOrThrow();
        authTokenService.revokeTokensForUser(admin);
        userService.invalidateCache(admin);
        adminAuditService.recordForActor(
                admin,
                AuditAction.ADMIN_LOGOUT,
                "USER",
                admin.getGuid(),
                "Logout administrativo realizado",
                null,
                null
        );
    }

    private AuthResponse generateTokens(User user, @Nullable AuthToken currentRefreshToken) {
        AuthToken accessToken = authTokenService.generateToken(user, AuthToken.TokenType.ACCESS);

        AuthToken refreshToken;
        if (currentRefreshToken == null) {
            refreshToken = authTokenService.generateToken(user, AuthToken.TokenType.REFRESH);
            authTokenService.persist(refreshToken);
        } else if (shouldRotateRefreshToken(currentRefreshToken)) {
            authTokenService.revoke(currentRefreshToken);
            refreshToken = authTokenService.generateToken(user, AuthToken.TokenType.REFRESH);
            authTokenService.persist(refreshToken);
        } else {
            refreshToken = currentRefreshToken;
        }

        return new AuthResponse(
                TokenDTO.from(accessToken),
                TokenDTO.from(refreshToken),
                UserDTO.from(user)
        );
    }

    private Duration refreshRotationThreshold() {
        return authConfig.getAccessToken().getDuration().multipliedBy(2);
    }

    private boolean shouldRotateRefreshToken(AuthToken refreshToken) {
        Duration remaining = Duration.between(Instant.now(), refreshToken.getExpiresAt());
        return remaining.compareTo(refreshRotationThreshold()) <= 0;
    }
}
