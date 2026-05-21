package org.imprime.ai.api.config;

import lombok.Data;
import org.imprime.ai.api.model.enums.LanguageCd;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "imprime.ai.api")
public class AppConfig {
    private LanguageCd defaultLanguage;
}
