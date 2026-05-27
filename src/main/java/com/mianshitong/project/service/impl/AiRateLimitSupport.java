package com.mianshitong.project.service.impl;

import com.mianshitong.project.common.constant.RedisKeys;
import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.po.RiskConfigPo;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiRateLimitSupport {

    private static final RedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(
        """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            if current > tonumber(ARGV[1]) then
                return 0
            end
            return current
            """,
        Long.class
    );

    private final StringRedisTemplate stringRedisTemplate;
    private final RiskConfigSupport riskConfigSupport;

    public void checkPerMinuteLimit(Long userId) {
        int limit = safeLimit(riskConfigSupport.current().getRateLimitPerMinute(), 10);
        boolean allowed = acquire(
            RedisKeys.aiMinuteLimit(userId),
            limit,
            Math.max(65, (int) Duration.ofMinutes(2).getSeconds())
        );
        if (!allowed) {
            throw new BizException("请求过于频繁，请稍后再试");
        }
    }

    public void acquireQuestionDailyQuota(Long userId) {
        RiskConfigPo config = riskConfigSupport.current();
        int limit = safeLimit(config.getMaxQuestionGeneratePerDay(), 50);
        boolean allowed = acquire(
            RedisKeys.aiQuestionDailyLimit(userId, LocalDate.now()),
            limit,
            secondsUntilTomorrow()
        );
        if (!allowed) {
            throw new BizException("今日生成题目次数已达上限");
        }
    }

    public void releaseQuestionDailyQuota(Long userId) {
        release(RedisKeys.aiQuestionDailyLimit(userId, LocalDate.now()));
    }

    public void acquireReportDailyQuota(Long userId) {
        RiskConfigPo config = riskConfigSupport.current();
        int limit = safeLimit(config.getMaxReportGeneratePerDay(), 5);
        boolean allowed = acquire(
            RedisKeys.aiReportDailyLimit(userId, LocalDate.now()),
            limit,
            secondsUntilTomorrow()
        );
        if (!allowed) {
            throw new BizException("今日报告生成次数已达上限");
        }
    }

    public void releaseReportDailyQuota(Long userId) {
        release(RedisKeys.aiReportDailyLimit(userId, LocalDate.now()));
    }

    private boolean acquire(String key, int limit, int expireSeconds) {
        Long result = stringRedisTemplate.execute(
            LIMIT_SCRIPT,
            List.of(key),
            String.valueOf(limit),
            String.valueOf(Math.max(expireSeconds, 1))
        );
        return result != null && result > 0;
    }

    private void release(String key) {
        Long current = stringRedisTemplate.opsForValue().decrement(key);
        if (current != null && current <= 0) {
            stringRedisTemplate.delete(key);
        }
    }

    private int secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tomorrowStart = LocalDate.now().plusDays(1).atStartOfDay();
        long seconds = Duration.between(now, tomorrowStart).getSeconds();
        return (int) Math.max(3600, seconds + 3600);
    }

    private int safeLimit(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }
}
