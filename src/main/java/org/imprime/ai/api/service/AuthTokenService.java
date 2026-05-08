package org.imprime.ai.api.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.config.AuthConfig;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.exception.InternalErrorException;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.repo.db.AuthTokenRepository;
import org.imprime.ai.api.util.HashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    @Value("{spring.application.name}")
    private String appName;

    private final AuthTokenRepository authTokenRepository;
    private final AuthConfig authConfig;

    public AuthToken generateToken(@NonNull User user, @NonNull AuthToken.TokenType tokenType) {
        return switch (tokenType) {
            case ACCESS -> generateToken(user, authConfig.getAccessToken());
            case REFRESH -> generateToken(user, authConfig.getRefreshToken());
        };
    }

    private AuthToken generateToken(@NonNull User user, @NonNull AuthConfig.TokenConfig tokenConfig) {
        try {
            var expiresAt = Instant.now().plus(tokenConfig.getDuration());
            var algorithm = Algorithm.HMAC256(tokenConfig.getKey());
            String token = JWT.create()
                    .withIssuer(appName)
                    .withSubject(user.getGuid())
                    .withExpiresAt(expiresAt)
                    .sign(algorithm);

            var authToken = new AuthToken();
            authToken.setExpiresAt(expiresAt);
            authToken.setToken(token);
            authToken.setTokenHash(HashUtil.sha256(token));
            authToken.setUserId(user.getId());
            authToken.setRevoked(false);
            authToken.setType(tokenConfig.getTokenType());

            return authToken;
        } catch (JWTCreationException e) {
            log.error("Unhandled error creating JWT Token", e);
            throw new InternalErrorException("Unexpected exception creating JWT Token");
        }
    }

    public DecodedJWT validateToken(
            @NonNull String token,
            @NonNull AuthToken.TokenType tokenType
    ) {
        return switch (tokenType) {
            case ACCESS -> validateToken(token, authConfig.getAccessToken());
            case REFRESH -> validateToken(token, authConfig.getRefreshToken());
        };
    }

    private DecodedJWT validateToken(@NonNull String token, @NonNull AuthConfig.TokenConfig tokenConfig) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(tokenConfig.getKey());

            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(appName)
                    .build();

            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            log.warn("Invalid JWT token", e);
            throw new UnauthorizedException();
        }
    }

    public void persist(AuthToken authToken) {
        if (!AuthToken.TokenType.REFRESH.equals(authToken.getType())) {
            log.warn("We only save REFRESH tokens. Token is of type [{}]", authToken.getType());
            return;
        }

        authTokenRepository.save(authToken);
    }

    public Optional<AuthToken> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String hash = HashUtil.sha256(token);

        return authTokenRepository.findAuthTokenByTokenHash(hash);
    }

    @Transactional
    public void revokeTokensForUser(User user) {
        authTokenRepository.revokeTokensByUserId(user.getId());
    }

    @Transactional
    public void revoke(AuthToken token) {
        authTokenRepository.revokeToken(token.getId());
    }
}
