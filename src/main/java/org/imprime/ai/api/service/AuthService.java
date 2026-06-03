package org.imprime.ai.api.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
    private final AuthConfig authConfig;
    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final BCryptPasswordEncoder passwordEncoder;

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

        return generateTokens(user);
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        if (request == null) {
            throw new UnauthorizedException();
        }

        DecodedJWT jwt = authTokenService.validateToken(request.token(), AuthToken.TokenType.REFRESH);

        String userGuid = jwt.getSubject();

        AuthToken refreshToken = authTokenService.findByToken(jwt.getToken())
                .orElseThrow(UnauthorizedException::new);

        if (refreshToken.getRevoked()) {
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
    public AuthResponse register(RegisterUserRequest request) {
        User user = userService.registerUser(request);
        return generateTokens(user);
    }
}
