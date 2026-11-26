package org.imprime.ai.api.service;

import org.imprime.ai.api.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class LegalDocumentServiceTest {
    private final AppConfig config = new AppConfig();
    private final MockEnvironment environment = new MockEnvironment();
    private final LegalDocumentService documents =
            new LegalDocumentService(mock(JdbcClient.class), config, environment);

    @Test
    void draftsAreAvailableOnlyWhenDevelopmentIsExplicit() {
        assertFalse(documents.allowsDraftAcceptance());

        config.setEnvironment(AppConfig.Environment.DEV);
        assertTrue(documents.allowsDraftAcceptance());

        config.setEnvironment(null);
        environment.setActiveProfiles("dev");
        assertTrue(documents.allowsDraftAcceptance());
    }

    @Test
    void productionOverridesDevelopmentSettings() {
        environment.setActiveProfiles("dev");
        config.setEnvironment(AppConfig.Environment.PROD);
        assertFalse(documents.allowsDraftAcceptance());

        config.setEnvironment(AppConfig.Environment.DEV);
        environment.setActiveProfiles("dev", "production");
        assertFalse(documents.allowsDraftAcceptance());
    }
}
