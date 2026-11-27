package org.imprime.ai.api.notification;

import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.auth.ResetPasswordRequest;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.SecurityEmailNotificationEvent;
import org.imprime.ai.api.notification.event.SecurityEmailNotificationEvent.SecurityEmailType;
import org.imprime.ai.api.notification.security.AccountSecurityService;
import org.imprime.ai.api.notification.security.UserSecurityToken;
import org.imprime.ai.api.notification.security.UserSecurityTokenPurpose;
import org.imprime.ai.api.notification.security.UserSecurityTokenRepository;
import org.imprime.ai.api.repo.db.AuthTokenRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.UserService;
import org.imprime.ai.api.util.HashUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserSecurityTokenRepository securityTokenRepository;
    @Mock
    private AuthTokenRepository authTokenRepository;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BCryptPasswordEncoder passwordEncoder;
    private NotificationProperties properties;
    private AccountSecurityService service;
    private Instant now;
    private User activeUser;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-11-27T14:00:00Z");
        passwordEncoder = new BCryptPasswordEncoder();
        properties = new NotificationProperties();
        properties.getSecurity().setPasswordResetTtl(Duration.ofMinutes(30));
        properties.getSecurity().setEmailVerificationTtl(Duration.ofHours(24));

        service = new AccountSecurityService(
                userRepository,
                securityTokenRepository,
                authTokenRepository,
                userService,
                passwordEncoder,
                properties,
                eventPublisher
        );
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now, ZoneOffset.UTC));

        activeUser = new User();
        activeUser.setId(42L);
        activeUser.setGuid("user-42-guid");
        activeUser.setFirstName("Ana");
        activeUser.setLastName("Paula");
        activeUser.setEmail("ana@imprimeai.shop");
        activeUser.setPasswordHash(passwordEncoder.encode("OldPass@123"));
        activeUser.setStatus(StatusCd.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        ServiceContext.clear();
    }

    @Test
    void requestPasswordResetIsSilentForUnknownOrInactiveUsersAntiEnumeration() {
        when(userRepository.findUserByEmail("missing@imprimeai.shop")).thenReturn(Optional.empty());

        service.requestPasswordReset("missing@imprimeai.shop");

        verifyNoInteractions(securityTokenRepository);
        verifyNoInteractions(eventPublisher);

        activeUser.setStatus(StatusCd.SUSPENDED);
        when(userRepository.findUserByEmail("ana@imprimeai.shop")).thenReturn(Optional.of(activeUser));

        service.requestPasswordReset("ana@imprimeai.shop");

        verifyNoInteractions(securityTokenRepository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void requestPasswordResetStoresHashedTokenAndPublishesEvent() {
        when(userRepository.findUserByEmail("ana@imprimeai.shop")).thenReturn(Optional.of(activeUser));
        when(securityTokenRepository.save(any(UserSecurityToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.requestPasswordReset("  ana@imprimeai.shop ");

        verify(securityTokenRepository).invalidateActiveTokensByUserAndPurpose(
                42L, UserSecurityTokenPurpose.PASSWORD_RESET, now);

        ArgumentCaptor<UserSecurityToken> tokenCaptor = ArgumentCaptor.forClass(UserSecurityToken.class);
        verify(securityTokenRepository).save(tokenCaptor.capture());

        ArgumentCaptor<SecurityEmailNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(SecurityEmailNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        SecurityEmailNotificationEvent event = eventCaptor.getValue();
        UserSecurityToken savedToken = tokenCaptor.getValue();

        assertEquals(SecurityEmailType.PASSWORD_RESET, event.type());
        assertNotNull(event.rawToken());
        assertNotEquals(event.rawToken(), savedToken.getTokenHash());
        assertEquals(HashUtil.sha256(event.rawToken()), savedToken.getTokenHash());
        assertEquals(now.plus(Duration.ofMinutes(30)), savedToken.getExpiresAt());
    }

    @Test
    void resetPasswordSucceedsRevokesSessionsAndPublishesConfirmation() {
        String rawToken = "valid-reset-token-123";
        UserSecurityToken token = new UserSecurityToken();
        token.setUserId(42L);
        token.setPurpose(UserSecurityTokenPurpose.PASSWORD_RESET);
        token.setTokenHash(HashUtil.sha256(rawToken));
        token.setTargetEmail("ana@imprimeai.shop");
        token.setExpiresAt(now.plus(Duration.ofMinutes(15)));

        when(securityTokenRepository.findByTokenHashAndPurpose(
                HashUtil.sha256(rawToken), UserSecurityTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(42L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        service.resetPassword(new ResetPasswordRequest(rawToken, "NovaSenha@123", "NovaSenha@123"));

        assertTrue(passwordEncoder.matches("NovaSenha@123", activeUser.getPasswordHash()));
        assertEquals(now, token.getUsedAt());
        verify(authTokenRepository).revokeTokensByUserId(42L);
        verify(userService).invalidateCache(activeUser);

        ArgumentCaptor<SecurityEmailNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(SecurityEmailNotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(SecurityEmailType.PASSWORD_CHANGED, eventCaptor.getValue().type());
    }

    @Test
    void resetPasswordRejectsExpiredOrUsedOrInvalidTokensAndWeakPasswords() {
        String expiredRaw = "expired-token";
        UserSecurityToken expired = new UserSecurityToken();
        expired.setUserId(42L);
        expired.setPurpose(UserSecurityTokenPurpose.PASSWORD_RESET);
        expired.setTokenHash(HashUtil.sha256(expiredRaw));
        expired.setExpiresAt(now.minusSeconds(1));

        when(securityTokenRepository.findByTokenHashAndPurpose(
                HashUtil.sha256(expiredRaw), UserSecurityTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(expired));

        assertThrows(BadRequestException.class, () ->
                service.resetPassword(new ResetPasswordRequest(expiredRaw, "NovaSenha@123", "NovaSenha@123")));

        String usedRaw = "used-token";
        UserSecurityToken used = new UserSecurityToken();
        used.setUserId(42L);
        used.setPurpose(UserSecurityTokenPurpose.PASSWORD_RESET);
        used.setTokenHash(HashUtil.sha256(usedRaw));
        used.setExpiresAt(now.plus(Duration.ofMinutes(10)));
        used.setUsedAt(now.minusSeconds(30));

        when(securityTokenRepository.findByTokenHashAndPurpose(
                HashUtil.sha256(usedRaw), UserSecurityTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(used));

        assertThrows(BadRequestException.class, () ->
                service.resetPassword(new ResetPasswordRequest(usedRaw, "NovaSenha@123", "NovaSenha@123")));

        assertThrows(BadRequestException.class, () ->
                service.resetPassword(new ResetPasswordRequest("any", "fraca", "fraca")));

        assertThrows(BadRequestException.class, () ->
                service.resetPassword(new ResetPasswordRequest("any", "NovaSenha@123", "OutraSenha@123")));

        verify(authTokenRepository, never()).revokeTokensByUserId(any());
    }

    @Test
    void verifyEmailMarksUserVerifiedAndRejectsTargetEmailMismatch() {
        String rawToken = "verify-email-token";
        UserSecurityToken token = new UserSecurityToken();
        token.setUserId(42L);
        token.setPurpose(UserSecurityTokenPurpose.EMAIL_VERIFICATION);
        token.setTokenHash(HashUtil.sha256(rawToken));
        token.setTargetEmail("ana@imprimeai.shop");
        token.setExpiresAt(now.plus(Duration.ofHours(12)));

        when(securityTokenRepository.findByTokenHashAndPurpose(
                HashUtil.sha256(rawToken), UserSecurityTokenPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(42L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(activeUser)).thenReturn(activeUser);

        service.verifyEmail(rawToken);

        assertEquals(now, activeUser.getEmailVerifiedAt());
        assertTrue(activeUser.isEmailVerified());
        assertEquals(now, token.getUsedAt());
        verify(userService).invalidateCache(activeUser);

        String wrongEmailRaw = "verify-old-email";
        UserSecurityToken staleEmailToken = new UserSecurityToken();
        staleEmailToken.setUserId(42L);
        staleEmailToken.setPurpose(UserSecurityTokenPurpose.EMAIL_VERIFICATION);
        staleEmailToken.setTokenHash(HashUtil.sha256(wrongEmailRaw));
        staleEmailToken.setTargetEmail("old@imprimeai.shop");
        staleEmailToken.setExpiresAt(now.plus(Duration.ofHours(12)));

        when(securityTokenRepository.findByTokenHashAndPurpose(
                HashUtil.sha256(wrongEmailRaw), UserSecurityTokenPurpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(staleEmailToken));

        assertThrows(BadRequestException.class, () -> service.verifyEmail(wrongEmailRaw));
    }

    @Test
    void resendVerificationSkipsVerifiedUserAndIssuesTokenForUnverifiedUser() {
        ServiceContext.getContext().setUser(activeUser);
        activeUser.setEmailVerifiedAt(now.minus(Duration.ofDays(1)));
        when(userRepository.findById(42L)).thenReturn(Optional.of(activeUser));

        service.resendVerificationForCurrentUser();
        verifyNoInteractions(securityTokenRepository);
        verifyNoInteractions(eventPublisher);

        activeUser.setEmailVerifiedAt(null);
        when(securityTokenRepository.save(any(UserSecurityToken.class))).thenAnswer(inv -> inv.getArgument(0));

        service.resendVerificationForCurrentUser();
        verify(securityTokenRepository).save(any(UserSecurityToken.class));
        verify(eventPublisher).publishEvent(any(SecurityEmailNotificationEvent.class));
    }
}
