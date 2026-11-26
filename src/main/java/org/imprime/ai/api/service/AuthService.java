package org.imprime.ai.api.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.AuthConfig;
import org.imprime.ai.api.http.request.auth.LoginRequest;
import org.imprime.ai.api.http.request.auth.RefreshTokenRequest;
import org.imprime.ai.api.http.request.user.RegisterUserRequest;
import org.imprime.ai.api.http.response.AuthResponse;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.dto.TokenDTO;
import org.imprime.ai.api.model.dto.UserDTO;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.repo.cache.MakerPresenceCache;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthConfig authConfig;
    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final MakerPresenceCache makerPresenceCache;
    private final LegalDocumentService legalDocuments;

    /**
     * This is meant to simulate a password hash comparison even when an email match isn't found, making it very much
     * harder to do timing attacks
     */
    private static final String DUMMY_BCRYPT = "$2a$10$7EqJtq98hPqEX7fNZaFWoOHi8uK2Q9uQ8QYq9Y5W5y5W5y5W5y5W";

    public AuthResponse login(LoginRequest request) {
        if (request == null) {
            throw new UnauthorizedException();
        }

        Optional<User> optionalUser = userService.findUserByEmail(request.email());
        if (optionalUser.isEmpty()) {
            passwordMatches(request.password(), DUMMY_BCRYPT);
            throw new UnauthorizedException();
        }

        User user = optionalUser.get();
        if (!passwordMatches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException();
        }

        if (!StatusCd.ACTIVE.equals(user.getStatus())) {
            throw new UnauthorizedException();
        }

        makerPresenceCache.markUserActivity(user);
        return generateTokens(user);
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
        refreshToken.setToken(jwt.getToken());

        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            throw new UnauthorizedException();
        }

        if (refreshToken.isExpired()) {
            throw new UnauthorizedException();
        }

        User user = userService.findUserById(refreshToken.getUserId())
                .orElseThrow(UnauthorizedException::new);

        if (!Objects.equals(user.getGuid(), userGuid)) {
            throw new UnauthorizedException();
        }

        if (!StatusCd.ACTIVE.equals(user.getStatus())) {
            throw new UnauthorizedException();
        }

        return generateTokens(user, refreshToken);
    }

    private AuthResponse generateTokens(User user) {
        return generateTokens(user, null);
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
        Instant now = Instant.now();

        Duration remaining = Duration.between(now, refreshToken.getExpiresAt());
        return remaining.compareTo(refreshRotationThreshold()) <= 0;
    }

    private boolean passwordMatches(String password, String passwordHash) {
        return passwordEncoder.matches(password, passwordHash);
    }

    @Transactional
    public void logout(@Nullable String authorizationHeader, @Nullable RefreshTokenRequest request) {
        if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String accessJwt = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
            if (!accessJwt.isBlank()) {
                try {
                    DecodedJWT jwt = authTokenService.validateToken(accessJwt, AuthToken.TokenType.ACCESS);
                    userService.findUserByGuid(jwt.getSubject())
                            .ifPresent(makerPresenceCache::markUserOffline);
                } catch (UnauthorizedException ignored) {
                    // Access token may already be expired when the user logs out.
                }
            }
        }

        if (request != null && request.token() != null && !request.token().isBlank()) {
            authTokenService.findByToken(request.token()).ifPresent(refreshToken -> {
                if (!Boolean.TRUE.equals(refreshToken.getRevoked())) {
                    authTokenService.revoke(refreshToken);
                }
                userService.findUserById(refreshToken.getUserId())
                        .ifPresent(makerPresenceCache::markUserOffline);
            });
        }
    }

    @Transactional
    public AuthResponse register(RegisterUserRequest request) {
        try {
            if (request == null || !Boolean.TRUE.equals(request.termsAccepted())
                    || !Boolean.TRUE.equals(request.privacyAcknowledged())) {
                throw new BadRequestException(MessageCd.LEGAL_ACCEPTANCE_REQUIRED);
            }
            long terms = legalDocuments.requireAcceptableVersion("terms", request.termsVersion());
            long privacy = legalDocuments.requireAcceptableVersion("privacy", request.privacyVersion());
            User user = userService.registerUser(request);
            legalDocuments.recordRegistrationAcceptance(user.getId(), terms, privacy);
            makerPresenceCache.markUserActivity(user);
            return generateTokens(user);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
