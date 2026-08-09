package com.eventrush.service;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "eventrush.queue.rabbit-enabled", havingValue = "true")
class RabbitGrabQueueConfig {

    @Bean
    DirectExchange grabExchange(@Value("${eventrush.queue.rabbit.exchange:eventrush.grab.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    Queue grabQueue(
            @Value("${eventrush.queue.rabbit.queue:eventrush.grab.queue}") String queue,
            @Value("${eventrush.queue.rabbit.dead-letter-exchange:eventrush.grab.dlx}") String deadLetterExchange,
            @Value("${eventrush.queue.rabbit.dead-letter-routing-key:eventrush.grab.dead}") String deadLetterRoutingKey
    ) {
        return new Queue(queue, true, false, false, Map.of(
                "x-dead-letter-exchange", deadLetterExchange,
                "x-dead-letter-routing-key", deadLetterRoutingKey
        ));
    }

    @Bean
    Binding grabBinding(
            @Qualifier("grabQueue") Queue grabQueue,
            @Qualifier("grabExchange") DirectExchange grabExchange,
            @Value("${eventrush.queue.rabbit.routing-key:eventrush.grab}") String routingKey
    ) {
        return BindingBuilder.bind(grabQueue).to(grabExchange).with(routingKey);
    }

    @Bean
    DirectExchange grabDeadLetterExchange(
            @Value("${eventrush.queue.rabbit.dead-letter-exchange:eventrush.grab.dlx}") String exchange
    ) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    Queue grabDeadLetterQueue(@Value("${eventrush.queue.rabbit.dead-letter-queue:eventrush.grab.dlq}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    Binding grabDeadLetterBinding(
            @Qualifier("grabDeadLetterQueue") Queue grabDeadLetterQueue,
            @Qualifier("grabDeadLetterExchange") DirectExchange grabDeadLetterExchange,
            @Value("${eventrush.queue.rabbit.dead-letter-routing-key:eventrush.grab.dead}") String routingKey
    ) {
        return BindingBuilder.bind(grabDeadLetterQueue).to(grabDeadLetterExchange).with(routingKey);
    }
}
