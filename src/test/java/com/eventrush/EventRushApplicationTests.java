package com.eventrush;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:eventrush-context-test;MODE=MySQL;DATABASE_TO_UPPER=false",
        "eventrush.stock.redis-enabled=false"
})
class EventRushApplicationTests {

    @Test
    void contextLoads() {
    }
}
