package org.imprime.ai.api.http.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.service.AuthTokenService;
import org.imprime.ai.api.service.UserService;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService authTokenService;
    private final UserService userService;

    private AuthType getAuthType(@NonNull HandlerMethod handlerMethod) {
        if (handlerMethod.getMethod().isAnnotationPresent(JwtAuthenticated.class)) {
            return AuthType.JWT;
        }

        if (handlerMethod.getMethod().isAnnotationPresent(SkipAuth.class)) {
            return AuthType.NO_AUTH;
        }

        return AuthType.JWT;
    }

    private enum AuthType {
        JWT, NO_AUTH
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        AuthType authType = getAuthType(handlerMethod);
        return switch (authType) {
            case JWT ->  handleJwtAuthentication(request);
            case NO_AUTH -> true;
        };
    }

    private boolean handleJwtAuthentication(@NonNull HttpServletRequest request) {
        String header = getHeaderToken(request).orElseThrow(UnauthorizedException::new);
        if (!header.startsWith(BEARER_PREFIX)) {
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

        ServiceContext ctx = ServiceContext.getContext();
        ctx.setUser(user);

        return true;
    }

    private Optional<String> getHeaderToken(@NonNull HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION));
    }
}
