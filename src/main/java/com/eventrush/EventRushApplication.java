package com.eventrush;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableRabbit
@EnableScheduling
@SpringBootApplication
public class EventRushApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventRushApplication.class, args);
    }
}
