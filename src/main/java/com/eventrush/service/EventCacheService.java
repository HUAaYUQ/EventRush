package com.eventrush.service;

import com.eventrush.domain.Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public EventCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<Event> getEvent(Long eventId) {
        String json = redisTemplate.opsForValue().get(cacheKey(eventId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, Event.class));
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(cacheKey(eventId));
            return Optional.empty();
        }
    }

    public void putEvent(Event event, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(cacheKey(event.id()), objectMapper.writeValueAsString(event), ttl);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("event cache serialization failed");
        }
    }

    public void evictEvent(Long eventId) {
        redisTemplate.delete(cacheKey(eventId));
    }

    private String cacheKey(Long eventId) {
        return "eventrush:event:" + eventId;
    }
}
