package com.mianshitong.project.service.impl;

import com.mianshitong.project.common.constant.RedisKeys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

@Component
@RequiredArgsConstructor
public class TokenBlacklistSupport {

    private final StringRedisTemplate stringRedisTemplate;

    public void blacklist(String token, Instant expireAt) {
        if (token == null || token.isBlank() || expireAt == null) {
            return;
        }
        long ttlSeconds = Duration.between(Instant.now(), expireAt).getSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
            RedisKeys.tokenBlacklist(digest(token)),
            "1",
            Duration.ofSeconds(ttlSeconds)
        );
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeys.tokenBlacklist(digest(token))));
    }

    private String digest(String token) {
        return DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
    }
}
