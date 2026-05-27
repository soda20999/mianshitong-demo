package com.mianshitong.project.service.impl;

import com.mianshitong.project.common.constant.RedisKeys;
import com.mianshitong.project.common.exception.BizException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AuthAttemptSupport {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.auth-security.login-max-failures:5}")
    private int loginMaxFailures;

    @Value("${app.auth-security.login-fail-window-minutes:15}")
    private int loginFailWindowMinutes;

    @Value("${app.auth-security.login-lock-minutes:15}")
    private int loginLockMinutes;

    public void assertLoginAllowed(String email, String clientIp) {
        String emailDigest = digestEmail(email);
        String ipSegment = normalizeIp(clientIp);
        try {
            boolean lockedByEmail = Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(RedisKeys.authLoginLockByEmail(emailDigest))
            );
            boolean lockedByIp = Boolean.TRUE.equals(
                stringRedisTemplate.hasKey(RedisKeys.authLoginLockByIp(ipSegment))
            );
            if (lockedByEmail || lockedByIp) {
                throw new BizException("登录失败次数过多，请稍后再试");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ignored) {
            // ignore redis errors to avoid auth service outage
        }
    }

    public void recordLoginFailure(String email, String clientIp) {
        String emailDigest = digestEmail(email);
        String ipSegment = normalizeIp(clientIp);
        Duration window = Duration.ofMinutes(Math.max(1, loginFailWindowMinutes));
        Duration lockDuration = Duration.ofMinutes(Math.max(1, loginLockMinutes));
        int maxFailures = Math.max(3, loginMaxFailures);
        try {
            long emailFailures = incrementWithTtl(RedisKeys.authLoginFailByEmail(emailDigest), window);
            long ipFailures = incrementWithTtl(RedisKeys.authLoginFailByIp(ipSegment), window);
            if (emailFailures >= maxFailures) {
                stringRedisTemplate.opsForValue().set(RedisKeys.authLoginLockByEmail(emailDigest), "1", lockDuration);
            }
            if (ipFailures >= maxFailures * 2L) {
                stringRedisTemplate.opsForValue().set(RedisKeys.authLoginLockByIp(ipSegment), "1", lockDuration);
            }
        } catch (Exception ignored) {
            // ignore redis errors to avoid auth service outage
        }
    }

    public void clearLoginFailure(String email, String clientIp) {
        String emailDigest = digestEmail(email);
        String ipSegment = normalizeIp(clientIp);
        try {
            stringRedisTemplate.delete(RedisKeys.authLoginFailByEmail(emailDigest));
            stringRedisTemplate.delete(RedisKeys.authLoginFailByIp(ipSegment));
            stringRedisTemplate.delete(RedisKeys.authLoginLockByEmail(emailDigest));
            stringRedisTemplate.delete(RedisKeys.authLoginLockByIp(ipSegment));
        } catch (Exception ignored) {
            // ignore redis errors to avoid auth service outage
        }
    }

    private long incrementWithTtl(String key, Duration ttl) {
        Long value = stringRedisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            stringRedisTemplate.expire(key, ttl);
        }
        return value == null ? 0L : value;
    }

    private String digestEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "unknown";
        }
        return clientIp.trim().replace(':', '_').replace('.', '_');
    }
}
