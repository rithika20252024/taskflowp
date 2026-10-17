package com.taskflow.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distributed Redis-backed rate limiter with seamless in-memory fallback.
 * Uses a fixed/sliding window algorithm.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    @Value("${taskflow.rate-limit.capacity:100}")
    private int capacity;

    private final StringRedisTemplate redisTemplate;

    // In-memory fallback tracking: key -> WindowCounter
    private final ConcurrentHashMap<String, WindowCounter> localCounters = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiterService(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean tryAcquire(String clientId) {
        if (redisTemplate != null) {
            try {
                String redisKey = "rate_limit:" + clientId + ":" + (System.currentTimeMillis() / 60000);
                Long currentCount = redisTemplate.opsForValue().increment(redisKey);
                if (currentCount != null && currentCount == 1L) {
                    redisTemplate.expire(redisKey, Duration.ofSeconds(65));
                }
                return currentCount != null && currentCount <= capacity;
            } catch (Exception e) {
                log.warn("Redis unavailable for rate limiting, falling back to local memory: {}", e.getMessage());
            }
        }

        // Fallback to local in-memory sliding window
        long currentMinute = System.currentTimeMillis() / 60000;
        String localKey = clientId + ":" + currentMinute;
        WindowCounter counter = localCounters.computeIfAbsent(localKey, k -> new WindowCounter(currentMinute));
        
        // Clean up older entries periodically
        if (localCounters.size() > 500) {
            localCounters.entrySet().removeIf(entry -> entry.getValue().minute < currentMinute - 1);
        }

        return counter.count.incrementAndGet() <= capacity;
    }

    private static class WindowCounter {
        final long minute;
        final AtomicInteger count = new AtomicInteger(0);

        WindowCounter(long minute) {
            this.minute = minute;
        }
    }
}
