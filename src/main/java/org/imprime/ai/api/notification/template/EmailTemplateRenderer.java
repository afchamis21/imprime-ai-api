package org.imprime.ai.api.notification.template;

import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.gateway.NonRetryableEmailException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateEngineException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Year;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;

/**
 * Renderiza templates HTML Thymeleaf para e-mails transacionais da imprimeai.shop e gera fallback em texto puro.
 */
@Slf4j
@Component
public class EmailTemplateRenderer {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final SpringTemplateEngine templateEngine;
    private final NotificationProperties properties;

    public EmailTemplateRenderer(
            @Qualifier("emailTemplateEngine") SpringTemplateEngine templateEngine,
            NotificationProperties properties) {
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    public RenderedEmail render(String templateName, Map<String, Object> variables) {
        if (templateName == null || templateName.isBlank()) {
            throw new NonRetryableEmailException("Nome do template de e-mail ausente.");
        }

        try {
            Context context = new Context(PT_BR);
            String baseUrl = properties.normalizedBaseUrl();
            context.setVariable("appName", properties.getMail().getFromName());
            context.setVariable("domainName", "imprimeai.shop");
            context.setVariable("publicBaseUrl", baseUrl);
            context.setVariable("preferencesUrl", baseUrl + "/profile?tab=profile");
            context.setVariable("supportEmail", properties.getMail().getReplyTo());
            context.setVariable("currentYear", Year.now(ZoneOffset.UTC).getValue());

            if (variables != null) {
                variables.forEach(context::setVariable);
            }

            String html = templateEngine.process(templateName, context);
            String text = extractPlainTextFallback(html);
            return new RenderedEmail(html, text);
        } catch (TemplateEngineException ex) {
            log.error("Erro ao renderizar template de e-mail [{}]: {}", templateName, ex.getMessage());
            throw new NonRetryableEmailException("Template de e-mail inválido: " + templateName, ex);
        }
    }

    static String extractPlainTextFallback(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String withoutHead = html.replaceAll("(?is)<head.*?>.*?</head>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<(br|hr)\\s*/?>", "\n")
                .replaceAll("(?is)</?(p|div|tr|h1|h2|h3|h4|li|section|header|footer)[^>]*>", "\n")
                .replaceAll("(?is)</?(td|th)[^>]*>", " ")
                .replaceAll("(?is)<[^>]+>", "");
        String unescaped = withoutHead
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return unescaped
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public record RenderedEmail(String htmlBody, String textBody) {
    }
}
