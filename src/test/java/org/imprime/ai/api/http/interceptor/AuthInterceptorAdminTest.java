package org.imprime.ai.api.http.interceptor;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.imprime.ai.api.controller.admin.AdminAuthController;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.auth.RequireAdmin;
import org.imprime.ai.api.http.request.auth.LoginRequest;
import org.imprime.ai.api.model.AuthToken;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.model.exception.ForbiddenException;
import org.imprime.ai.api.model.exception.UnauthorizedException;
import org.imprime.ai.api.service.AuthTokenService;
import org.imprime.ai.api.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorAdminTest {

    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private UserService userService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private AuthInterceptor interceptor;

    static class AnnotatedAdminController {
        @RequireAdmin
        public void secured() {
        }
    }

    static class UnannotatedAdminController {
        public void endpointWithoutAnnotation() {
        }
    }

    @BeforeEach
    void setUp() {
        interceptor = new AuthInterceptor(authTokenService, userService);
        ServiceContext.clear();
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void rejectsMissingAuthorizationOnAdminEndpoint() throws Exception {
        HandlerMethod handler = handler(new AnnotatedAdminController(), "secured");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/admin/users");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> interceptor.preHandle(request, response, handler));
    }

    @Test
    void rejectsBuyerAndMakerRolesOnRequireAdminEndpoint() throws Exception {
        HandlerMethod handler = handler(new AnnotatedAdminController(), "secured");
        mockValidTokenForUser("user-buyer", UserRole.BUYER, StatusCd.ACTIVE, null);

        ForbiddenException buyerEx = assertThrows(
                ForbiddenException.class,
                () -> interceptor.preHandle(request, response, handler)
        );
        assertEquals(MessageCd.ADMIN_ROLE_REQUIRED, buyerEx.getMessageCd());

        ServiceContext.clear();
        mockValidTokenForUser("user-maker", UserRole.MAKER, StatusCd.ACTIVE, 10L);

        ForbiddenException makerEx = assertThrows(
                ForbiddenException.class,
                () -> interceptor.preHandle(request, response, handler)
        );
        assertEquals(MessageCd.ADMIN_ROLE_REQUIRED, makerEx.getMessageCd());
    }

    @Test
    void rejectsSuspendedAdminOnAdminEndpoint() throws Exception {
        HandlerMethod handler = handler(new AnnotatedAdminController(), "secured");
        mockValidTokenForUser("admin-suspended", UserRole.ADMIN, StatusCd.SUSPENDED, null);

        assertThrows(UnauthorizedException.class, () -> interceptor.preHandle(request, response, handler));
    }

    @Test
    void allowsActiveAdminOnRequireAdminEndpoint() throws Exception {
        HandlerMethod handler = handler(new AnnotatedAdminController(), "secured");
        User admin = mockValidTokenForUser("admin-1", UserRole.ADMIN, StatusCd.ACTIVE, null);

        boolean allowed = interceptor.preHandle(request, response, handler);

        assertTrue(allowed);
        assertSame(admin, ServiceContext.getUser().orElse(null));
    }

    @Test
    void enforcesAdminRoleByDefenseInDepthOnUnannotatedApiAdminUri() throws Exception {
        HandlerMethod handler = handler(new UnannotatedAdminController(), "endpointWithoutAnnotation");
        when(request.getRequestURI()).thenReturn("/api/admin/internal-check");
        mockValidTokenForUser("buyer-1", UserRole.BUYER, StatusCd.ACTIVE, null);

        ForbiddenException ex = assertThrows(
                ForbiddenException.class,
                () -> interceptor.preHandle(request, response, handler)
        );
        assertEquals(MessageCd.ADMIN_ROLE_REQUIRED, ex.getMessageCd());

        ServiceContext.clear();
        mockValidTokenForUser("admin-2", UserRole.ADMIN, StatusCd.ACTIVE, null);
        assertTrue(interceptor.preHandle(request, response, handler));
    }

    @Test
    void allowsSkipAuthOnAdminLoginEndpoint() throws Exception {
        Method loginMethod = AdminAuthController.class.getMethod("login", LoginRequest.class);
        HandlerMethod handler = new HandlerMethod(new AdminAuthController(null, null), loginMethod);

        when(request.getMethod()).thenReturn("POST");

        boolean allowed = interceptor.preHandle(request, response, handler);

        assertTrue(allowed);
        verifyNoInteractions(authTokenService, userService);
    }

    private HandlerMethod handler(Object bean, String methodName) throws NoSuchMethodException {
        Method method = bean.getClass().getMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    private User mockValidTokenForUser(String guid, UserRole role, StatusCd status, Long companyId) {
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer valid-jwt");

        DecodedJWT jwt = mock(DecodedJWT.class);
        when(jwt.getSubject()).thenReturn(guid);
        when(authTokenService.validateToken("valid-jwt", AuthToken.TokenType.ACCESS)).thenReturn(jwt);

        User user = new User();
        user.setId(99L);
        user.setGuid(guid);
        user.setRole(role);
        user.setStatus(status);
        user.setCompanyId(companyId);
        when(userService.findUserByGuid(guid)).thenReturn(Optional.of(user));
        return user;
    }
}
