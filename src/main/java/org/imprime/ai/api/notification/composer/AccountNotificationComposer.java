package org.imprime.ai.api.notification.composer;

import lombok.RequiredArgsConstructor;
import org.imprime.ai.api.model.Company;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.model.enums.UserRole;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.event.SecurityEmailNotificationEvent;
import org.imprime.ai.api.notification.event.UserRegisteredNotificationEvent;
import org.imprime.ai.api.notification.messaging.EmailCategory;
import org.imprime.ai.api.notification.messaging.EmailQueueMessage;
import org.imprime.ai.api.notification.template.NotificationFormatting;
import org.imprime.ai.api.repo.db.CompanyRepository;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta mensagens transacionais de boas-vindas, verificação de e-mail e segurança de conta.
 */
@Service
@RequiredArgsConstructor
public class AccountNotificationComposer {
    private final NotificationProperties properties;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<EmailQueueMessage> buildWelcomeEmail(UserRegisteredNotificationEvent event) {
        if (event == null || event.userId() == null) {
            return List.of();
        }
        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null || !StatusCd.ACTIVE.equals(user.getStatus())
                || user.getEmail() == null || user.getEmail().isBlank()) {
            return List.of();
        }

        boolean isMaker = user.getCompanyId() != null || UserRole.MAKER.equals(user.getRole());
        String studioName = null;
        if (isMaker && user.getCompanyId() != null) {
            studioName = companyRepository.findById(user.getCompanyId())
                    .map(Company::getName)
                    .orElse(null);
        }

        String baseUrl = properties.normalizedBaseUrl();
        String ctaUrl = isMaker ? baseUrl + "/dashboard" : baseUrl + "/marketplace";
        String ctaLabel = isMaker ? "Configurar meu estúdio" : "Explorar marketplace";
        String subject = isMaker
                ? "Bem-vindo(a) à Imprime AÍ — seu estúdio Maker está pronto"
                : "Bem-vindo(a) à Imprime AÍ!";

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("subject", subject);
        vars.put("preheader", isMaker
                ? "Configure sua oficina 3D e comece a receber orçamentos e pedidos."
                : "Encontre Makers verificados, modelos 3D e encomendas sob demanda.");
        vars.put("firstName", user.getFirstName());
        vars.put("isMaker", isMaker);
        vars.put("studioName", studioName);
        vars.put("ctaLabel", ctaLabel);
        vars.put("ctaUrl", ctaUrl);

        return List.of(new EmailQueueMessage(
                "welcome:" + user.getId(),
                EmailCategory.SECURITY_ACCOUNT,
                "account/welcome",
                user.getEmail(),
                user.getFullName(),
                subject,
                vars,
                Instant.now()
        ));
    }

    public List<EmailQueueMessage> buildSecurityEmail(SecurityEmailNotificationEvent event) {
        if (event == null || event.recipientEmail() == null || event.recipientEmail().isBlank() || event.type() == null) {
            return List.of();
        }
        String baseUrl = properties.normalizedBaseUrl();
        String firstName = NotificationFormatting.safeText(event.recipientFirstName(), "Usuário");
        String recipientEmail = event.recipientEmail().trim();
        Instant now = Instant.now();

        return switch (event.type()) {
            case EMAIL_VERIFICATION -> {
                String encoded = encodeQueryParam(event.rawToken());
                String ctaUrl = baseUrl + "/verify-email?token=" + encoded;
                String subject = "Confirme seu endereço de e-mail — Imprime AÍ";
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("subject", subject);
                vars.put("preheader", "Confirme seu endereço de e-mail para proteger sua conta na Imprime AÍ.");
                vars.put("firstName", firstName);
                vars.put("emailAddress", recipientEmail);
                vars.put("expiresAtFormatted", NotificationFormatting.formatInstantBr(event.expiresAt()));
                vars.put("ctaUrl", ctaUrl);

                yield List.of(new EmailQueueMessage(
                        "verify-email:" + event.userId() + ":" + now.toEpochMilli(),
                        EmailCategory.SECURITY_ACCOUNT,
                        "account/verify-email",
                        recipientEmail,
                        firstName,
                        subject,
                        vars,
                        now
                ));
            }
            case PASSWORD_RESET -> {
                String encoded = encodeQueryParam(event.rawToken());
                String ctaUrl = baseUrl + "/reset-password?token=" + encoded;
                String subject = "Redefinição de senha solicitada — Imprime AÍ";
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("subject", subject);
                vars.put("preheader", "Use o link de uso único para definir uma nova senha com segurança.");
                vars.put("firstName", firstName);
                vars.put("expiresAtFormatted", NotificationFormatting.formatInstantBr(event.expiresAt()));
                vars.put("ctaUrl", ctaUrl);

                yield List.of(new EmailQueueMessage(
                        "password-reset:" + event.userId() + ":" + now.toEpochMilli(),
                        EmailCategory.SECURITY_ACCOUNT,
                        "account/password-reset",
                        recipientEmail,
                        firstName,
                        subject,
                        vars,
                        now
                ));
            }
            case PASSWORD_CHANGED -> {
                String ctaUrl = baseUrl + "/forgot-password";
                String subject = "Sua senha foi alterada — Imprime AÍ";
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("subject", subject);
                vars.put("preheader", "Confirmamos que a senha da sua conta na Imprime AÍ foi redefinida.");
                vars.put("firstName", firstName);
                vars.put("changedAtFormatted", NotificationFormatting.formatInstantBr(event.expiresAt() != null ? event.expiresAt() : now));
                vars.put("ctaUrl", ctaUrl);

                yield List.of(new EmailQueueMessage(
                        "password-changed:" + event.userId() + ":" + now.toEpochMilli(),
                        EmailCategory.SECURITY_ACCOUNT,
                        "account/password-changed",
                        recipientEmail,
                        firstName,
                        subject,
                        vars,
                        now
                ));
            }
        };
    }

    private static String encodeQueryParam(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
