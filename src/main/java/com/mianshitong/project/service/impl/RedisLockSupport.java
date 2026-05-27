package com.mianshitong.project.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisLockSupport {

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
        """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
        Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    public void unlock(String key, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        stringRedisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
    }
}
