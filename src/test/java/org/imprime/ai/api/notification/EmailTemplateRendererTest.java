package org.imprime.ai.api.notification;

import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.gateway.NonRetryableEmailException;
import org.imprime.ai.api.notification.template.EmailTemplateRenderer;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateRendererTest {

    @Test
    void injectsBaseVariablesAndGeneratesPlainTextFallback() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        NotificationProperties props = new NotificationProperties();
        props.setPublicBaseUrl("https://imprimeai.shop/");

        EmailTemplateRenderer renderer = new EmailTemplateRenderer(engine, props);
        String template = """
                <html><head><style>body{color:#000;}</style></head>
                <body>
                  <h1>Olá, <span th:text="${buyerName}">Cliente</span>!</h1>
                  <p>Base: <span th:text="${publicBaseUrl}">url</span></p>
                  <p>Pref: <span th:text="${preferencesUrl}">pref</span></p>
                </body></html>
                """;

        EmailTemplateRenderer.RenderedEmail rendered = renderer.render(template, Map.of("buyerName", "Ana"));
        assertTrue(rendered.htmlBody().contains("Olá, <span>Ana</span>!"));
        assertTrue(rendered.htmlBody().contains("https://imprimeai.shop"));
        assertTrue(rendered.textBody().contains("Olá, Ana!"));
        assertFalse(rendered.textBody().contains("body{color:#000;}"));
    }

    @Test
    void throwsNonRetryableWhenTemplateNameMissing() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(new SpringTemplateEngine(), new NotificationProperties());
        assertThrows(NonRetryableEmailException.class, () -> renderer.render(" ", Map.of()));
    }
}
