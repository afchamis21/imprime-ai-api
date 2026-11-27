package org.imprime.ai.api.notification.security;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.http.ServiceContext;
import org.imprime.ai.api.http.request.auth.ResetPasswordRequest;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.MessageCd;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.exception.BadRequestException;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.SecurityEmailNotificationEvent;
import org.imprime.ai.api.notification.event.SecurityEmailNotificationEvent.SecurityEmailType;
import org.imprime.ai.api.repo.db.AuthTokenRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.imprime.ai.api.service.UserService;
import org.imprime.ai.api.util.HashUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Gerencia tokens seguros de uso único para redefinição de senha e verificação de e-mail.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSecurityService {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).+$");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserSecurityTokenRepository securityTokenRepository;
    private final AuthTokenRepository authTokenRepository;
    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final NotificationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            return;
        }
        String trimmedEmail = rawEmail.trim();
        User user = userRepository.findUserByEmail(trimmedEmail)
                .or(() -> userRepository.findUserByEmail(trimmedEmail.toLowerCase(Locale.ROOT)))
                .orElse(null);
        if (user == null || !StatusCd.ACTIVE.equals(user.getStatus()) || user.getEmail() == null || user.getEmail().isBlank()) {
            // Resposta silenciosa anti-enumeração para contas inexistentes ou inativas
            return;
        }

        Instant now = clock.instant();
        Duration ttl = properties.getSecurity().getPasswordResetTtl() != null
                ? properties.getSecurity().getPasswordResetTtl()
                : Duration.ofMinutes(30);
        Instant expiresAt = now.plus(ttl);

        String rawToken = generateRawToken();
        securityTokenRepository.invalidateActiveTokensByUserAndPurpose(user.getId(), UserSecurityTokenPurpose.PASSWORD_RESET, now);

        UserSecurityToken tokenEntity = new UserSecurityToken();
        tokenEntity.setUserId(user.getId());
        tokenEntity.setPurpose(UserSecurityTokenPurpose.PASSWORD_RESET);
        tokenEntity.setTokenHash(HashUtil.sha256(rawToken));
        tokenEntity.setTargetEmail(user.getEmail());
        tokenEntity.setExpiresAt(expiresAt);
        tokenEntity.setCreatedAt(now);
        securityTokenRepository.save(tokenEntity);

        eventPublisher.publishEvent(new SecurityEmailNotificationEvent(
                SecurityEmailType.PASSWORD_RESET,
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                rawToken,
                expiresAt
        ));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request == null || request.token() == null || request.token().isBlank()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }
        validatePasswordPair(request.newPassword(), request.confirmPassword());

        Instant now = clock.instant();
        String hash = HashUtil.sha256(request.token().trim());
        UserSecurityToken tokenEntity = securityTokenRepository
                .findByTokenHashAndPurpose(hash, UserSecurityTokenPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));

        if (tokenEntity.getUsedAt() != null || tokenEntity.getExpiresAt() == null || tokenEntity.getExpiresAt().isBefore(now)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        User user = userRepository.findById(tokenEntity.getUserId())
                .filter(u -> StatusCd.ACTIVE.equals(u.getStatus()))
                .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));

        tokenEntity.setUsedAt(now);
        securityTokenRepository.save(tokenEntity);
        securityTokenRepository.invalidateActiveTokensByUserAndPurpose(user.getId(), UserSecurityTokenPurpose.PASSWORD_RESET, now);

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        User saved = userRepository.save(user);
        authTokenRepository.revokeTokensByUserId(saved.getId());
        userService.invalidateCache(saved);

        if (saved.getEmail() != null && !saved.getEmail().isBlank()) {
            eventPublisher.publishEvent(new SecurityEmailNotificationEvent(
                    SecurityEmailType.PASSWORD_CHANGED,
                    saved.getId(),
                    saved.getEmail(),
                    saved.getFirstName(),
                    null,
                    now
            ));
        }
    }

    @Transactional
    public void issueEmailVerificationForUser(User user) {
        if (user == null || user.getId() == null || !StatusCd.ACTIVE.equals(user.getStatus())
                || user.isEmailVerified() || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        Instant now = clock.instant();
        Duration ttl = properties.getSecurity().getEmailVerificationTtl() != null
                ? properties.getSecurity().getEmailVerificationTtl()
                : Duration.ofHours(24);
        Instant expiresAt = now.plus(ttl);

        String rawToken = generateRawToken();
        securityTokenRepository.invalidateActiveTokensByUserAndPurpose(user.getId(), UserSecurityTokenPurpose.EMAIL_VERIFICATION, now);

        UserSecurityToken tokenEntity = new UserSecurityToken();
        tokenEntity.setUserId(user.getId());
        tokenEntity.setPurpose(UserSecurityTokenPurpose.EMAIL_VERIFICATION);
        tokenEntity.setTokenHash(HashUtil.sha256(rawToken));
        tokenEntity.setTargetEmail(user.getEmail());
        tokenEntity.setExpiresAt(expiresAt);
        tokenEntity.setCreatedAt(now);
        securityTokenRepository.save(tokenEntity);

        eventPublisher.publishEvent(new SecurityEmailNotificationEvent(
                SecurityEmailType.EMAIL_VERIFICATION,
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                rawToken,
                expiresAt
        ));
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        Instant now = clock.instant();
        String hash = HashUtil.sha256(rawToken.trim());
        UserSecurityToken tokenEntity = securityTokenRepository
                .findByTokenHashAndPurpose(hash, UserSecurityTokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));

        if (tokenEntity.getUsedAt() != null || tokenEntity.getExpiresAt() == null || tokenEntity.getExpiresAt().isBefore(now)) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        User user = userRepository.findById(tokenEntity.getUserId())
                .filter(u -> StatusCd.ACTIVE.equals(u.getStatus()))
                .orElseThrow(() -> new BadRequestException(MessageCd.VALIDATION_ERROR));

        if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(tokenEntity.getTargetEmail())) {
            throw new BadRequestException(MessageCd.VALIDATION_ERROR);
        }

        tokenEntity.setUsedAt(now);
        securityTokenRepository.save(tokenEntity);

        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            User saved = userRepository.save(user);
            userService.invalidateCache(saved);
        }
    }

    @Transactional
    public void resendVerificationForCurrentUser() {
        User caller = ServiceContext.getUserOrThrow();
        User freshUser = userRepository.findById(caller.getId()).orElse(caller);
        issueEmailVerificationForUser(freshUser);
    }

    private static void validatePasswordPair(String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException(MessageCd.MISSING_USER_PASSWORD);
        }
        if (newPassword.length() < 8 || newPassword.length() > 32 || !PASSWORD_PATTERN.matcher(newPassword).matches()) {
            throw new BadRequestException(MessageCd.INVALID_USER_PASSWORD);
        }
        if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BadRequestException(MessageCd.USER_PASSWORD_MISMATCH);
        }
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
