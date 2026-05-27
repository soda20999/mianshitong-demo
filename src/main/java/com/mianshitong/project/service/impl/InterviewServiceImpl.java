package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.common.constant.RedisKeys;
import com.mianshitong.project.common.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mianshitong.project.entity.bo.AiCallResult;
import com.mianshitong.project.entity.bo.AiUsage;
import com.mianshitong.project.entity.dto.AnswerInterviewRequest;
import com.mianshitong.project.entity.dto.StartInterviewRequest;
import com.mianshitong.project.entity.po.InterviewMessagePo;
import com.mianshitong.project.entity.po.InterviewSessionPo;
import com.mianshitong.project.entity.po.QuestionPo;
import com.mianshitong.project.entity.po.QuestionSetPo;
import com.mianshitong.project.entity.po.ReportPo;
import com.mianshitong.project.entity.po.ScoreDetailPo;
import com.mianshitong.project.entity.vo.InterviewReplyVo;
import com.mianshitong.project.enum_.InterviewStyle;
import com.mianshitong.project.enum_.TaskStatus;
import com.mianshitong.project.mapper.InterviewSessionMapper;
import com.mianshitong.project.mapper.QuestionSetMapper;
import com.mianshitong.project.mapper.ReportMapper;
import com.mianshitong.project.service.InterviewService;
import com.mianshitong.project.service.QuestionService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewSessionMapper interviewSessionMapper;
    private final QuestionSetMapper questionSetMapper;
    private final ReportMapper reportMapper;
    private final QuestionService questionService;
    private final SpringAiEngine springAiEngine;
    private final AiLogSupport aiLogSupport;
    private final AiRateLimitSupport aiRateLimitSupport;
    private final RedisLockSupport redisLockSupport;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.redis.interview-context-max-messages:30}")
    private int interviewContextMaxMessages;

    @Value("${app.redis.interview-context-ttl-hours:24}")
    private long interviewContextTtlHours;

    @Value("${app.redis.report-task-status-ttl-hours:72}")
    private long reportTaskStatusTtlHours;

    @Override
    public InterviewSessionPo start(Long userId, StartInterviewRequest request) {
        QuestionSetPo questionSet = questionService.getOwnedQuestionSet(userId, request.questionSetId());
        InterviewStyle style = parseStyle(request.style());
        if (questionSet.getQuestions() == null || questionSet.getQuestions().isEmpty()) {
            throw new BizException("题集为空，无法开始面试");
        }

        InterviewSessionPo session = new InterviewSessionPo();
        session.setUserId(userId);
        session.setQuestionSetId(questionSet.getId());
        session.setTitle(request.title());
        session.setStyle(style);
        session.setStatus("RUNNING");
        session.setCreatedAt(LocalDateTime.now());
        session.setMessages(new ArrayList<>());
        session.setScoreHistory(new ArrayList<>());
        session.setFavoriteQuestionIds(new LinkedHashSet<>());
        session.setWrongQuestionIds(new LinkedHashSet<>());

        QuestionPo firstQuestion = questionSet.getQuestions().get(0);
        session.getMessages().add(message("ai", firstQuestion.getContent()));
        interviewSessionMapper.insert(session);
        saveContextMessages(session.getId(), session.getMessages());
        return session;
    }

    @Override
    public InterviewReplyVo answer(Long userId, Long sessionId, AnswerInterviewRequest request) {
        aiRateLimitSupport.checkPerMinuteLimit(userId);
        InterviewSessionPo session = requireOwnedSession(userId, sessionId);
        if (!"RUNNING".equals(session.getStatus())) {
            throw new BizException("当前会话已结束");
        }
        if (session.getMessages() == null) {
            session.setMessages(new ArrayList<>());
        }
        if (session.getScoreHistory() == null) {
            session.setScoreHistory(new ArrayList<>());
        }
        if (session.getFavoriteQuestionIds() == null) {
            session.setFavoriteQuestionIds(new LinkedHashSet<>());
        }
        if (session.getWrongQuestionIds() == null) {
            session.setWrongQuestionIds(new LinkedHashSet<>());
        }

        List<InterviewMessagePo> contextMessages = loadContextMessages(sessionId, session.getMessages());

        InterviewMessagePo userMessage = message("user", request.answer());
        session.getMessages().add(userMessage);
        contextMessages.add(userMessage);
        contextMessages = trimContextMessages(contextMessages);

        QuestionPo current = currentQuestion(session);
        String currentQuestionContent = current == null ? "" : current.getContent();

        AiCallResult<ScoreDetailPo> scoreResult = springAiEngine.scoreAnswer(
            session.getStyle(),
            currentQuestionContent,
            request.answer(),
            contextMessages
        );
        ScoreDetailPo scoreDetail = scoreResult.data();
        session.getScoreHistory().add(scoreDetail);

        if (current != null && scoreDetail.getTotal() < 75) {
            session.getWrongQuestionIds().add(current.getId());
        }

        AiCallResult<String> followUpResult = springAiEngine.generateFollowUp(
            session.getStyle(),
            currentQuestionContent,
            request.answer(),
            contextMessages
        );
        String followUp = followUpResult.data();
        InterviewMessagePo aiMessage = message("ai", followUp);
        session.getMessages().add(aiMessage);
        contextMessages.add(aiMessage);
        contextMessages = trimContextMessages(contextMessages);

        interviewSessionMapper.updateById(session);
        saveContextMessages(sessionId, contextMessages);

        AiUsage usage = scoreResult.usage().plus(followUpResult.usage());
        aiLogSupport.log(userId, "interview", usage, "SUCCESS");
        return new InterviewReplyVo(sessionId, followUp, scoreDetail);
    }

    @Override
    public InterviewSessionPo finish(Long userId, Long sessionId) {
        String lockKey = RedisKeys.reportGenerateLock(sessionId);
        String lockToken = redisLockSupport.tryLock(lockKey, Duration.ofSeconds(15));
        if (lockToken == null) {
            throw new BizException("报告生成请求处理中，请勿重复提交");
        }

        boolean reportQuotaConsumed = false;
        boolean reportCreated = false;
        try {
            InterviewSessionPo session = requireOwnedSession(userId, sessionId);
            if ("FINISHED".equals(session.getStatus())) {
                return session;
            }

            aiRateLimitSupport.checkPerMinuteLimit(userId);
            aiRateLimitSupport.acquireReportDailyQuota(userId);
            reportQuotaConsumed = true;

            session.setStatus("FINISHED");
            session.setFinishedAt(LocalDateTime.now());
            int total = session.getScoreHistory() == null || session.getScoreHistory().isEmpty()
                ? 0
                : session.getScoreHistory().stream().mapToInt(ScoreDetailPo::getTotal).sum() / session.getScoreHistory().size();
            session.setTotalScore(total);
            interviewSessionMapper.updateById(session);

            ReportPo report = new ReportPo();
            report.setUserId(userId);
            report.setInterviewId(sessionId);
            report.setStatus(TaskStatus.PENDING);
            report.setOverallScore(null);
            report.setCreatedAt(LocalDateTime.now());
            report.setUpdatedAt(LocalDateTime.now());
            reportMapper.insert(report);
            reportCreated = true;
            enqueueReportTask(report.getId());

            aiLogSupport.log(userId, "report", 0, 0, "PENDING");
            return session;
        } catch (RuntimeException ex) {
            if (reportQuotaConsumed && !reportCreated) {
                aiRateLimitSupport.releaseReportDailyQuota(userId);
            }
            throw ex;
        } finally {
            redisLockSupport.unlock(lockKey, lockToken);
        }
    }

    @Override
    public List<InterviewSessionPo> listByUser(Long userId) {
        return interviewSessionMapper.selectList(
            new LambdaQueryWrapper<InterviewSessionPo>()
                .eq(InterviewSessionPo::getUserId, userId)
                .orderByDesc(InterviewSessionPo::getCreatedAt)
        );
    }

    @Override
    public Map<String, Object> historyOverview(Long userId) {
        List<InterviewSessionPo> interviews = interviewSessionMapper.selectList(
            new LambdaQueryWrapper<InterviewSessionPo>()
                .eq(InterviewSessionPo::getUserId, userId)
                .eq(InterviewSessionPo::getStatus, "FINISHED")
                .orderByDesc(InterviewSessionPo::getCreatedAt)
        );

        Set<Long> questionSetIds = interviews.stream()
            .map(InterviewSessionPo::getQuestionSetId)
            .filter(id -> id != null && id > 0)
            .collect(Collectors.toSet());
        Map<Long, QuestionSetPo> questionSetMap = questionSetIds.isEmpty()
            ? Map.of()
            : questionSetMapper.selectBatchIds(questionSetIds).stream()
                .collect(Collectors.toMap(QuestionSetPo::getId, item -> item));

        List<Map<String, Object>> wrongQuestions = new ArrayList<>();
        List<Map<String, Object>> favorites = new ArrayList<>();
        List<Integer> growth = new ArrayList<>();

        for (InterviewSessionPo interview : interviews) {
            if (interview.getTotalScore() != null) {
                growth.add(interview.getTotalScore());
            }
            QuestionSetPo questionSet = questionSetMap.get(interview.getQuestionSetId());
            if (questionSet == null || questionSet.getQuestions() == null) {
                continue;
            }
            Map<Long, String> questionMap = questionSet.getQuestions().stream()
                .collect(LinkedHashMap::new, (map, q) -> map.put(q.getId(), q.getContent()), LinkedHashMap::putAll);

            Set<Long> wrongIds = interview.getWrongQuestionIds() == null ? Set.of() : interview.getWrongQuestionIds();
            for (Long id : wrongIds) {
                wrongQuestions.add(Map.of(
                    "interviewId", interview.getId(),
                    "questionId", id,
                    "question", questionMap.getOrDefault(id, "题目已删除")
                ));
            }
            Set<Long> favoriteIds = interview.getFavoriteQuestionIds() == null ? Set.of() : interview.getFavoriteQuestionIds();
            for (Long id : favoriteIds) {
                favorites.add(Map.of(
                    "interviewId", interview.getId(),
                    "questionId", id,
                    "question", questionMap.getOrDefault(id, "题目已删除")
                ));
            }
        }

        return Map.of(
            "interviews", interviews,
            "favorites", favorites,
            "wrongQuestions", wrongQuestions,
            "growthScores", growth
        );
    }

    private InterviewSessionPo requireOwnedSession(Long userId, Long sessionId) {
        InterviewSessionPo session = interviewSessionMapper.selectOne(
            new LambdaQueryWrapper<InterviewSessionPo>()
                .eq(InterviewSessionPo::getId, sessionId)
                .eq(InterviewSessionPo::getUserId, userId)
                .last("LIMIT 1")
        );
        if (session == null) {
            throw new BizException("会话不存在");
        }
        return session;
    }

    private InterviewMessagePo message(String role, String content) {
        InterviewMessagePo message = new InterviewMessagePo();
        message.setRole(role);
        message.setContent(content);
        message.setTime(LocalDateTime.now());
        return message;
    }

    private QuestionPo currentQuestion(InterviewSessionPo session) {
        QuestionSetPo questionSet = questionSetMapper.selectById(session.getQuestionSetId());
        if (questionSet == null || questionSet.getQuestions() == null || questionSet.getQuestions().isEmpty()) {
            return null;
        }
        long answerCount = session.getMessages().stream().filter(item -> "user".equals(item.getRole())).count();
        int index = (int) Math.max(0, answerCount - 1);
        if (index >= questionSet.getQuestions().size()) {
            index = questionSet.getQuestions().size() - 1;
        }
        return questionSet.getQuestions().get(index);
    }

    private InterviewStyle parseStyle(String style) {
        try {
            return InterviewStyle.valueOf(style.toUpperCase());
        } catch (Exception ex) {
            throw new BizException("面试风格不合法");
        }
    }

    private List<InterviewMessagePo> loadContextMessages(Long sessionId, List<InterviewMessagePo> fallbackMessages) {
        String key = RedisKeys.interviewContext(sessionId);
        List<String> cached = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (cached != null && !cached.isEmpty()) {
            List<InterviewMessagePo> messages = cached.stream()
                .map(this::deserializeMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
            if (!messages.isEmpty()) {
                return trimContextMessages(messages);
            }
        }

        List<InterviewMessagePo> source = fallbackMessages == null
            ? new ArrayList<>()
            : new ArrayList<>(fallbackMessages);
        source = trimContextMessages(source);
        saveContextMessages(sessionId, source);
        return source;
    }

    private void saveContextMessages(Long sessionId, List<InterviewMessagePo> messages) {
        String key = RedisKeys.interviewContext(sessionId);
        stringRedisTemplate.delete(key);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<String> payload = messages.stream()
            .map(this::serializeMessage)
            .filter(Objects::nonNull)
            .toList();
        if (payload.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForList().rightPushAll(key, payload);
        stringRedisTemplate.expire(key, Duration.ofHours(Math.max(1, interviewContextTtlHours)));
    }

    private List<InterviewMessagePo> trimContextMessages(List<InterviewMessagePo> messages) {
        int limit = interviewContextMaxMessages <= 0 ? 30 : interviewContextMaxMessages;
        if (messages.size() <= limit) {
            return messages;
        }
        return new ArrayList<>(messages.subList(messages.size() - limit, messages.size()));
    }

    private String serializeMessage(InterviewMessagePo message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private InterviewMessagePo deserializeMessage(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, InterviewMessagePo.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private void enqueueReportTask(Long reportId) {
        if (reportId == null) {
            return;
        }
        String statusKey = RedisKeys.reportTaskStatus(reportId);
        stringRedisTemplate.opsForValue().set(
            statusKey,
            TaskStatus.PENDING.name(),
            Duration.ofHours(Math.max(1, reportTaskStatusTtlHours))
        );
        stringRedisTemplate.opsForList().rightPush(RedisKeys.REPORT_TASK_QUEUE_KEY, String.valueOf(reportId));
    }
}
