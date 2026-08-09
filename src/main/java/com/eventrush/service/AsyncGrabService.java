package com.eventrush.service;

import com.eventrush.domain.TicketOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AsyncGrabService {

    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    private final TicketingService ticketingService;
    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final boolean rabbitQueueEnabled;
    private final boolean redisQueueEnabled;
    private final String queueKey;
    private final String rabbitExchange;
    private final String rabbitRoutingKey;
    private final long resultTtlSeconds;
    private final LinkedBlockingQueue<GrabMessage> memoryQueue = new LinkedBlockingQueue<>();
    private final Map<String, GrabResult> memoryResults = new ConcurrentHashMap<>();

    public AsyncGrabService(
            TicketingService ticketingService,
            StringRedisTemplate redisTemplate,
            ObjectProvider<RabbitTemplate> rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${eventrush.queue.rabbit-enabled:false}") boolean rabbitQueueEnabled,
            @Value("${eventrush.queue.redis-enabled:false}") boolean redisQueueEnabled,
            @Value("${eventrush.queue.grab-key:eventrush:queue:grab}") String queueKey,
            @Value("${eventrush.queue.rabbit.exchange:eventrush.grab.exchange}") String rabbitExchange,
            @Value("${eventrush.queue.rabbit.routing-key:eventrush.grab}") String rabbitRoutingKey,
            @Value("${eventrush.queue.result-ttl-seconds:600}") long resultTtlSeconds
    ) {
        this.ticketingService = ticketingService;
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate.getIfAvailable();
        this.objectMapper = objectMapper;
        this.rabbitQueueEnabled = rabbitQueueEnabled;
        this.redisQueueEnabled = redisQueueEnabled;
        this.queueKey = queueKey;
        this.rabbitExchange = rabbitExchange;
        this.rabbitRoutingKey = rabbitRoutingKey;
        this.resultTtlSeconds = resultTtlSeconds;
    }

    public GrabResult submitGrab(Long userId, Long sessionId, Long ticketCategoryId) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        GrabMessage message = new GrabMessage(requestId, userId, sessionId, ticketCategoryId);
        GrabResult result = GrabResult.pending(requestId);
        saveResult(result);
        enqueue(message);
        return result;
    }

    public GrabResult getResult(String requestId) {
        if (!usesRedisResultStore()) {
            GrabResult result = memoryResults.get(requestId);
            if (result == null) {
                throw new BusinessException("grab request not found");
            }
            return result;
        }
        String json = redisTemplate.opsForValue().get(resultKey(requestId));
        if (json == null) {
            throw new BusinessException("grab request not found");
        }
        try {
            return objectMapper.readValue(json, GrabResult.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("grab result deserialization failed");
        }
    }

    @Scheduled(fixedDelayString = "${eventrush.queue.consumer-scan-ms:500}")
    void consumeOne() {
        if (rabbitQueueEnabled) {
            return;
        }
        Optional<GrabMessage> message = dequeue();
        message.ifPresent(this::process);
    }

    @RabbitListener(
            queues = "${eventrush.queue.rabbit.queue:eventrush.grab.queue}",
            autoStartup = "${eventrush.queue.rabbit-enabled:false}"
    )
    void consumeRabbit(String json) {
        try {
            process(objectMapper.readValue(json, GrabMessage.class));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("grab rabbit message deserialization failed");
        }
    }

    private void enqueue(GrabMessage message) {
        if (rabbitQueueEnabled) {
            rabbitTemplate.convertAndSend(rabbitExchange, rabbitRoutingKey, writeJson(message));
            return;
        }
        if (!redisQueueEnabled) {
            memoryQueue.offer(message);
            return;
        }
        redisTemplate.opsForList().leftPush(queueKey, writeJson(message));
    }

    private Optional<GrabMessage> dequeue() {
        if (!redisQueueEnabled) {
            return Optional.ofNullable(memoryQueue.poll());
        }
        String json = redisTemplate.opsForList().rightPop(queueKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, GrabMessage.class));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("grab message deserialization failed");
        }
    }

    private void process(GrabMessage message) {
        try {
            TicketOrder order = ticketingService.grabTicket(message.userId(), message.sessionId(), message.ticketCategoryId());
            saveResult(GrabResult.success(message.requestId(), order.id()));
        } catch (BusinessException exception) {
            saveResult(GrabResult.failed(message.requestId(), exception.getMessage()));
        }
    }

    private void saveResult(GrabResult result) {
        if (!usesRedisResultStore()) {
            memoryResults.put(result.requestId(), result);
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    resultKey(result.requestId()),
                    objectMapper.writeValueAsString(result),
                    Duration.ofSeconds(resultTtlSeconds)
            );
        } catch (JsonProcessingException exception) {
            throw new BusinessException("grab result serialization failed");
        }
    }

    private boolean usesRedisResultStore() {
        return redisQueueEnabled && !rabbitQueueEnabled;
    }

    private String writeJson(GrabMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("grab message serialization failed");
        }
    }

    private String resultKey(String requestId) {
        return "eventrush:grab-result:" + requestId;
    }

    record GrabMessage(String requestId, Long userId, Long sessionId, Long ticketCategoryId) {
    }

    public record GrabResult(String requestId, String status, Long orderId, String errorMessage) {

        static GrabResult pending(String requestId) {
            return new GrabResult(requestId, PENDING, null, null);
        }

        static GrabResult success(String requestId, Long orderId) {
            return new GrabResult(requestId, SUCCESS, orderId, null);
        }

        static GrabResult failed(String requestId, String errorMessage) {
            return new GrabResult(requestId, FAILED, null, errorMessage);
        }
    }
}
