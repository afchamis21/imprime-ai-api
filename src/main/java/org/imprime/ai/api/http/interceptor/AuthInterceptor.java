package org.imprime.ai.api.http.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.auth.JwtAuthenticated;
import org.imprime.ai.api.http.auth.OptionalAuth;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.auth.RequireMaker;
import org.imprime.ai.api.http.auth.SkipAuth;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.notification.chat.UserPresenceCache;
import org.imprime.ai.api.repo.cache.MakerPresenceCache;
import org.imprime.ai.api.service.AuthTokenService;
import org.imprime.ai.api.service.UserService;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService authTokenService;
    private final UserService userService;
    private final MakerPresenceCache makerPresenceCache;
    private final UserPresenceCache userPresenceCache;

    public AuthInterceptor(
            AuthTokenService authTokenService,
            UserService userService,
            MakerPresenceCache makerPresenceCache) {
        this(authTokenService, userService, makerPresenceCache, null);
    }

    @Autowired
    public AuthInterceptor(
            AuthTokenService authTokenService,
            UserService userService,
            MakerPresenceCache makerPresenceCache,
            UserPresenceCache userPresenceCache) {
        this.authTokenService = authTokenService;
        this.userService = userService;
        this.makerPresenceCache = makerPresenceCache;
        this.userPresenceCache = userPresenceCache;
    }

    private AuthType getAuthType(@NonNull HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Class<?> beanType = handlerMethod.getBeanType();

        AuthType methodAuth = resolveFromAnnotatedElement(method);
        if (methodAuth != null) {
            return methodAuth;
        }

        AuthType classAuth = resolveFromAnnotatedElement(beanType);
        return classAuth != null ? classAuth : AuthType.JWT;
    }

    private AuthType resolveFromAnnotatedElement(java.lang.reflect.AnnotatedElement element) {
        if (hasAnnotation(element, RequireAdmin.class)) {
            return AuthType.REQUIRE_ADMIN;
        }
        if (hasAnnotation(element, RequireMaker.class)) {
            return AuthType.REQUIRE_MAKER;
        }
        if (hasAnnotation(element, JwtAuthenticated.class)) {
            return AuthType.JWT;
        }
        if (hasAnnotation(element, OptionalAuth.class)) {
            return AuthType.OPTIONAL;
        }
        if (hasAnnotation(element, SkipAuth.class)) {
            return AuthType.NO_AUTH;
        }
        return null;
    }

    private boolean hasAnnotation(java.lang.reflect.AnnotatedElement element, Class<? extends Annotation> annotationType) {
        return element.isAnnotationPresent(annotationType);
    }

    private enum AuthType {
        REQUIRE_ADMIN, REQUIRE_MAKER, JWT, OPTIONAL, NO_AUTH
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        AuthType authType = getAuthType(handlerMethod);
        if (authType != AuthType.NO_AUTH && isAdminEndpoint(request)) {
            authType = AuthType.REQUIRE_ADMIN;
        }

        return switch (authType) {
            case REQUIRE_ADMIN -> handleAdminAuthentication(request);
            case REQUIRE_MAKER -> handleMakerAuthentication(request);
            case JWT -> handleJwtAuthentication(request);
            case OPTIONAL -> handleOptionalAuthentication(request);
            case NO_AUTH -> true;
        };
    }

    private boolean isAdminEndpoint(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && ("/api/admin".equals(uri) || uri.startsWith("/api/admin/"));
    }

    private boolean handleAdminAuthentication(@NonNull HttpServletRequest request) {
        handleJwtAuthentication(request);
        User user = ServiceContext.getUserOrThrow();
        if (!user.isAdmin()) {
            log.warn("User [{}] with role [{}] attempted to access Admin-only endpoint [{}]",
                    user.getGuid(), user.getRole(), request.getRequestURI());
            throw new ForbiddenException(MessageCd.ADMIN_ROLE_REQUIRED);
        }
        return true;
    }

    private boolean handleMakerAuthentication(@NonNull HttpServletRequest request) {
        handleJwtAuthentication(request);
        User user = ServiceContext.getUserOrThrow();
        if (user.getCompanyId() == null) {
            log.warn("User [{}] attempted to access Maker-only endpoint without associated company", user.getGuid());
            throw new ForbiddenException();
        }
        return true;
    }

    private boolean handleOptionalAuthentication(@NonNull HttpServletRequest request) {
        Optional<String> headerOpt = getHeaderToken(request);
        if (headerOpt.isEmpty() || headerOpt.get().isBlank()) {
            return true;
        }
        return handleJwtAuthentication(request);
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
        makerPresenceCache.markUserActivity(user);
        if (userPresenceCache != null) {
            userPresenceCache.markActive(user);
        }

        return true;
    }

    private Optional<String> getHeaderToken(@NonNull HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION));
    }
}
