package com.mianshitong.project.common.constant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class RedisKeys {

    private static final String PREFIX = "xz:";
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    public static final String REPORT_TASK_QUEUE_KEY = PREFIX + "report:task:queue";
    public static final String HOT_RANKING_ZSET_KEY = PREFIX + "hot:ranking";
    public static final String HOT_VIEW_HASH_KEY = PREFIX + "hot:metric:view";
    public static final String HOT_FAVORITE_HASH_KEY = PREFIX + "hot:metric:favorite";
    public static final String HOT_PRACTICE_HASH_KEY = PREFIX + "hot:metric:practice";
    public static final String HOT_CACHE_KEYS_SET_KEY = PREFIX + "hot:cache:keys";
    public static final String HOT_INDEX_READY_KEY = PREFIX + "hot:index:ready";

    private RedisKeys() {
    }

    public static String aiMinuteLimit(Long userId) {
        return PREFIX + "ai:limit:minute:" + userId + ":" + LocalDateTime.now().format(MINUTE_FORMATTER);
    }

    public static String aiQuestionDailyLimit(Long userId, LocalDate date) {
        return PREFIX + "ai:limit:question:day:" + userId + ":" + date.format(DAY_FORMATTER);
    }

    public static String aiReportDailyLimit(Long userId, LocalDate date) {
        return PREFIX + "ai:limit:report:day:" + userId + ":" + date.format(DAY_FORMATTER);
    }

    public static String interviewContext(Long sessionId) {
        return PREFIX + "interview:context:" + sessionId;
    }

    public static String reportTaskStatus(Long reportId) {
        return PREFIX + "report:task:status:" + reportId;
    }

    public static String reportGenerateLock(Long sessionId) {
        return PREFIX + "lock:report:generate:" + sessionId;
    }

    public static String reportTaskLock(Long reportId) {
        return PREFIX + "lock:report:task:" + reportId;
    }

    public static String questionGenerateLock(Long userId, String signature) {
        return PREFIX + "lock:question:generate:" + userId + ":" + signature;
    }

    public static String hotListCache(String tag, String position) {
        return PREFIX + "hot:list:" + normalizeSegment(tag) + ":" + normalizeSegment(position);
    }

    public static String tokenBlacklist(String tokenDigest) {
        return PREFIX + "auth:blacklist:" + tokenDigest;
    }

    public static String authLoginFailByEmail(String emailDigest) {
        return PREFIX + "auth:login:fail:email:" + normalizeSegment(emailDigest);
    }

    public static String authLoginFailByIp(String ipSegment) {
        return PREFIX + "auth:login:fail:ip:" + normalizeSegment(ipSegment);
    }

    public static String authLoginLockByEmail(String emailDigest) {
        return PREFIX + "auth:login:lock:email:" + normalizeSegment(emailDigest);
    }

    public static String authLoginLockByIp(String ipSegment) {
        return PREFIX + "auth:login:lock:ip:" + normalizeSegment(ipSegment);
    }

    private static String normalizeSegment(String value) {
        if (value == null || value.isBlank()) {
            return "all";
        }
        return value.trim().toLowerCase()
            .replaceAll("\\s+", "_")
            .replace(":", "_");
    }
}
