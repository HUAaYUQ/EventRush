package com.eventrush.service;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setLocation(new ClassPathResource("lua/rate-limit.lua"));
        this.rateLimitScript.setResultType(Long.class);
    }

    public boolean allowGrab(Long userId, int limit, int windowSeconds) {
        Long result = redisTemplate.execute(
                rateLimitScript,
                List.of("eventrush:rate:grab:" + userId),
                String.valueOf(limit),
                String.valueOf(windowSeconds)
        );
        return result != null && result == 1L;
    }
}
