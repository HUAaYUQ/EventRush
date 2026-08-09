package com.eventrush.service;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "eventrush.queue.rocket-enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = "${eventrush.queue.rocket.topic:eventrush-grab-topic}",
        consumerGroup = "${eventrush.queue.rocket.consumer-group:eventrush-grab-consumer}"
)
class RocketGrabConsumer implements RocketMQListener<String> {

    private final AsyncGrabService asyncGrabService;

    RocketGrabConsumer(AsyncGrabService asyncGrabService) {
        this.asyncGrabService = asyncGrabService;
    }

    @Override
    public void onMessage(String message) {
        asyncGrabService.consumeRocket(message);
    }
}
