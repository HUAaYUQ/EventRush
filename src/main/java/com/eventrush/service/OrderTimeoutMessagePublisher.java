package com.eventrush.service;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "eventrush.queue.rocket-enabled", havingValue = "true")
public class OrderTimeoutMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final String topic;
    private final int delayLevel;

    public OrderTimeoutMessagePublisher(
            ObjectProvider<RocketMQTemplate> rocketMQTemplate,
            @Value("${eventrush.order.timeout-topic:eventrush-order-timeout-topic}") String topic,
            @Value("${eventrush.order.timeout-delay-level:16}") int delayLevel
    ) {
        this.rocketMQTemplate = rocketMQTemplate.getIfAvailable();
        this.topic = topic;
        this.delayLevel = delayLevel;
    }

    public void publish(Long orderId) {
        if (rocketMQTemplate == null) {
            return;
        }
        rocketMQTemplate.syncSend(
                topic,
                MessageBuilder.withPayload(orderId.toString()).build(),
                3000,
                delayLevel
        );
    }
}
