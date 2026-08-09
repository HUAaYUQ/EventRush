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
    private final ObjectMapper objectMapper;
    private final boolean redisQueueEnabled;
    private final String queueKey;
    private final long resultTtlSeconds;
    private final LinkedBlockingQueue<GrabMessage> memoryQueue = new LinkedBlockingQueue<>();
    private final Map<String, GrabResult> memoryResults = new ConcurrentHashMap<>();

    public AsyncGrabService(
            TicketingService ticketingService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${eventrush.queue.redis-enabled:false}") boolean redisQueueEnabled,
            @Value("${eventrush.queue.grab-key:eventrush:queue:grab}") String queueKey,
            @Value("${eventrush.queue.result-ttl-seconds:600}") long resultTtlSeconds
    ) {
        this.ticketingService = ticketingService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisQueueEnabled = redisQueueEnabled;
        this.queueKey = queueKey;
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
        if (!redisQueueEnabled) {
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
        Optional<GrabMessage> message = dequeue();
        message.ifPresent(this::process);
    }

    private void enqueue(GrabMessage message) {
        if (!redisQueueEnabled) {
            memoryQueue.offer(message);
            return;
        }
        try {
            redisTemplate.opsForList().leftPush(queueKey, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("grab message serialization failed");
        }
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
        if (!redisQueueEnabled) {
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
