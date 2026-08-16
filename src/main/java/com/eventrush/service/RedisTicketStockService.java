package com.eventrush.service;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisTicketStockService {

    public static final long STOCK_NOT_ENOUGH = -1L;
    public static final long DUPLICATE_GRAB = -2L;
    public static final long STOCK_NOT_INITIALIZED = -3L;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> grabScript;

    public RedisTicketStockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.grabScript = new DefaultRedisScript<>();
        this.grabScript.setLocation(new ClassPathResource("lua/grab-ticket.lua"));
        this.grabScript.setResultType(Long.class);
    }

    public void preloadStock(Long sessionId, Long ticketCategoryId, int stock) {
        redisTemplate.opsForValue().set(stockKey(sessionId, ticketCategoryId), String.valueOf(stock));
        // ponytail: dev preload resets grabbed users; rebuild from paid/pending orders after MySQL is introduced.
        redisTemplate.delete(grabbedUsersKey(sessionId, ticketCategoryId));
    }

    public long tryDeduct(Long userId, Long sessionId, Long ticketCategoryId, int quantity) {
        Long result = redisTemplate.execute(
                grabScript,
                List.of(stockKey(sessionId, ticketCategoryId), grabbedUsersKey(sessionId, ticketCategoryId)),
                String.valueOf(userId),
                String.valueOf(quantity)
        );
        if (result == null) {
            throw new BusinessException("redis stock deduction failed");
        }
        return result;
    }

    public void release(Long userId, Long sessionId, Long ticketCategoryId, int quantity) {
        redisTemplate.opsForValue().increment(stockKey(sessionId, ticketCategoryId), quantity);
        redisTemplate.opsForSet().remove(grabbedUsersKey(sessionId, ticketCategoryId), String.valueOf(userId));
    }

    private String stockKey(Long sessionId, Long ticketCategoryId) {
        return "eventrush:stock:" + sessionId + ":" + ticketCategoryId;
    }

    private String grabbedUsersKey(Long sessionId, Long ticketCategoryId) {
        return "eventrush:grabbed:" + sessionId + ":" + ticketCategoryId;
    }
}
