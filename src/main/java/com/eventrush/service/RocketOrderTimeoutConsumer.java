package com.eventrush.service;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "eventrush.queue.rocket-enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${eventrush.order.timeout-topic:eventrush-order-timeout-topic}",
        consumerGroup = "${eventrush.order.timeout-consumer-group:eventrush-order-timeout-consumer}"
)
class RocketOrderTimeoutConsumer implements RocketMQListener<String> {

    private final TicketingService ticketingService;

    RocketOrderTimeoutConsumer(TicketingService ticketingService) {
        this.ticketingService = ticketingService;
    }

    @Override
    public void onMessage(String message) {
        ticketingService.cancelExpiredOrder(Long.valueOf(message));
    }
}
