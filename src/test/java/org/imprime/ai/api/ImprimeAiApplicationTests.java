package org.imprime.ai.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class ImprimeAiApplicationTests {

    @Test
    void contextLoads() {
    }

}
