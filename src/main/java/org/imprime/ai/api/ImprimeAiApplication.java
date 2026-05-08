package org.imprime.ai.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class ImprimeAiApplication {

    static void main(String[] args) {
        SpringApplication.run(ImprimeAiApplication.class, args);
    }

}
