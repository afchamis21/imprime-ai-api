package org.imprime.ai.api.notification;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.core.net.RequestOptions;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.imprime.ai.api.notification.config.NotificationProperties;
import org.imprime.ai.api.notification.gateway.EmailGateway;
import org.imprime.ai.api.notification.gateway.EmailMessage;
import org.imprime.ai.api.notification.gateway.EmailSendResult;
import org.imprime.ai.api.notification.gateway.NoOpEmailGateway;
import org.imprime.ai.api.notification.gateway.NonRetryableEmailException;
import org.imprime.ai.api.notification.gateway.ResendEmailGateway;
import org.imprime.ai.api.notification.gateway.RetryableEmailException;
import org.imprime.ai.api.notification.gateway.SmtpEmailGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendEmailGatewayTest {

    @Mock
    private Resend resendClient;
    @Mock
    private Emails emailsApi;

    private ResendEmailGateway gateway;

    @BeforeEach
    void setUp() {
        NotificationProperties props = new NotificationProperties();
        props.getMail().setFromAddress("nao-responda@imprimeai.shop");
        props.getMail().setFromName("Imprime AÍ");
        props.getMail().setResendApiKey("re_test_123");
        gateway = new ResendEmailGateway(props, resendClient);
    }

    @Test
    void sendsWithSenderAndIdempotencyKey() throws Exception {
        when(resendClient.emails()).thenReturn(emailsApi);
        when(emailsApi.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .thenReturn(new CreateEmailResponse("resend_id_99"));

        EmailSendResult result = gateway.send(new EmailMessage(
                "evt-resend-1",
                "buyer@imprimeai.shop",
                null,
                "Confirmação",
                "<p>Teste</p>",
                "Teste",
                "TRANSACTIONAL_ORDER"
        ));

        assertEquals("RESEND", result.provider());
        assertEquals("resend_id_99", result.messageId());

        ArgumentCaptor<CreateEmailOptions> optCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        ArgumentCaptor<RequestOptions> reqCaptor = ArgumentCaptor.forClass(RequestOptions.class);
        verify(emailsApi).send(optCaptor.capture(), reqCaptor.capture());
        assertEquals("Imprime AÍ <nao-responda@imprimeai.shop>", optCaptor.getValue().getFrom());
        assertEquals("evt-resend-1", reqCaptor.getValue().getIdempotencyKey());
    }

    @Test
    void maps4xxClientValidationToNonRetryableAnd5xxToRetryable() throws Exception {
        when(resendClient.emails()).thenReturn(emailsApi);
        when(emailsApi.send(any(CreateEmailOptions.class), any(RequestOptions.class)))
                .thenThrow(new ResendException("validation_error", 422, "bad address"))
                .thenThrow(new ResendException("server_error", 503, "temporarily unavailable"));

        EmailMessage msg = new EmailMessage(
                "evt-err", "buyer@imprimeai.shop", null, "Subj", "<p>A</p>", "A", "CAT"
        );

        assertThrows(NonRetryableEmailException.class, () -> gateway.send(msg));
        assertThrows(RetryableEmailException.class, () -> gateway.send(msg));
    }

    @Test
    void selectsSmtpOrResendGatewayByMailProviderProperty() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(
                        GatewayPropertyTestConfig.class,
                        SmtpEmailGateway.class,
                        ResendEmailGateway.class,
                        NoOpEmailGateway.class
                )
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class));

        runner.withPropertyValues("imprime.ai.notification.mail.provider=smtp")
                .run(ctx -> {
                    assertEquals(1, ctx.getBeansOfType(EmailGateway.class).size());
                    assertInstanceOf(SmtpEmailGateway.class, ctx.getBean(EmailGateway.class));
                });

        runner.withPropertyValues("imprime.ai.notification.mail.provider=resend")
                .run(ctx -> {
                    assertEquals(1, ctx.getBeansOfType(EmailGateway.class).size());
                    assertInstanceOf(ResendEmailGateway.class, ctx.getBean(EmailGateway.class));
                });
    }

    @EnableConfigurationProperties(NotificationProperties.class)
    static class GatewayPropertyTestConfig {
    }
}
