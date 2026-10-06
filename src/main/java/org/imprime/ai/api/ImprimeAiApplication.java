package org.imprime.ai.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@ComponentScan(
        basePackages = "org.imprime.ai",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "org\\.imprime\\.ai\\.api\\.model\\.converter\\.base\\..*"
        )
)
@SpringBootApplication
@EnableConfigurationProperties
public class ImprimeAiApplication {

    static void main(String[] args) {
        SpringApplication.run(ImprimeAiApplication.class, args);
    }

}
