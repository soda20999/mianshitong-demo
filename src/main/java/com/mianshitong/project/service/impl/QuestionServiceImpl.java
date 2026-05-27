package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.mianshitong.project.common.constant.RedisKeys;
import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.bo.AiCallResult;
import com.mianshitong.project.entity.dto.QuestionGenerateRequest;
import com.mianshitong.project.entity.po.QuestionPo;
import com.mianshitong.project.entity.po.QuestionSetPo;
import com.mianshitong.project.entity.po.ResumePo;
import com.mianshitong.project.mapper.QuestionSetMapper;
import com.mianshitong.project.mapper.ResumeMapper;
import com.mianshitong.project.service.QuestionService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.util.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private final ResumeMapper resumeMapper;
    private final QuestionSetMapper questionSetMapper;
    private final SpringAiEngine springAiEngine;
    private final AiLogSupport aiLogSupport;
    private final AiRateLimitSupport aiRateLimitSupport;
    private final RedisLockSupport redisLockSupport;

    @Override
    public QuestionSetPo generate(Long userId, QuestionGenerateRequest request) {
        ResumePo resume = resumeMapper.selectOne(
            new LambdaQueryWrapper<ResumePo>()
                .eq(ResumePo::getId, request.resumeId())
                .eq(ResumePo::getUserId, userId)
                .last("LIMIT 1")
        );
        if (resume == null) {
            throw new BizException("简历不存在或无权限");
        }

        QuestionSetPo reusable = findReusableQuestionSet(userId, request);
        if (reusable != null) {
            return reusable;
        }

        String lockKey = RedisKeys.questionGenerateLock(userId, requestSignature(request));
        String lockToken = redisLockSupport.tryLock(lockKey, Duration.ofSeconds(15));
        if (lockToken == null) {
            throw new BizException("题目生成请求处理中，请勿重复提交");
        }
        boolean dailyQuotaConsumed = false;
        boolean questionSetCreated = false;
        try {
            QuestionSetPo lockedReusable = findReusableQuestionSet(userId, request);
            if (lockedReusable != null) {
                return lockedReusable;
            }

            aiRateLimitSupport.checkPerMinuteLimit(userId);
            aiRateLimitSupport.acquireQuestionDailyQuota(userId);
            dailyQuotaConsumed = true;

            AiCallResult<List<QuestionPo>> aiResult = springAiEngine.generateQuestions(
                request.jobTitle(),
                request.direction(),
                request.level(),
                request.companyStyle(),
                request.categories(),
                request.count(),
                resume.getContent(),
                resume.getParseResult()
            );
            List<QuestionPo> questions = aiResult.data();
            questions.forEach(item -> item.setId(IdWorker.getId()));

            QuestionSetPo questionSet = new QuestionSetPo();
            questionSet.setUserId(userId);
            questionSet.setResumeId(request.resumeId());
            questionSet.setJobTitle(request.jobTitle());
            questionSet.setDirection(request.direction());
            questionSet.setLevel(request.level());
            questionSet.setCompanyStyle(request.companyStyle());
            questionSet.setQuestions(questions);
            questionSet.setCreatedAt(LocalDateTime.now());
            questionSetMapper.insert(questionSet);
            questionSetCreated = true;

            aiLogSupport.log(userId, "question", aiResult.usage(), "SUCCESS");
            return questionSet;
        } catch (RuntimeException ex) {
            if (dailyQuotaConsumed && !questionSetCreated) {
                aiRateLimitSupport.releaseQuestionDailyQuota(userId);
            }
            throw ex;
        } finally {
            redisLockSupport.unlock(lockKey, lockToken);
        }
    }

    @Override
    public List<QuestionSetPo> listByUser(Long userId) {
        return questionSetMapper.selectList(
            new LambdaQueryWrapper<QuestionSetPo>()
                .eq(QuestionSetPo::getUserId, userId)
                .orderByDesc(QuestionSetPo::getCreatedAt)
        );
    }

    @Override
    public QuestionSetPo getOwnedQuestionSet(Long userId, Long questionSetId) {
        QuestionSetPo questionSet = questionSetMapper.selectOne(
            new LambdaQueryWrapper<QuestionSetPo>()
                .eq(QuestionSetPo::getId, questionSetId)
                .eq(QuestionSetPo::getUserId, userId)
                .last("LIMIT 1")
        );
        if (questionSet == null) {
            throw new BizException("题集不存在");
        }
        return questionSet;
    }

    private QuestionSetPo findReusableQuestionSet(Long userId, QuestionGenerateRequest request) {
        QuestionSetPo latest = questionSetMapper.selectOne(
            new LambdaQueryWrapper<QuestionSetPo>()
                .eq(QuestionSetPo::getUserId, userId)
                .eq(QuestionSetPo::getResumeId, request.resumeId())
                .eq(QuestionSetPo::getJobTitle, request.jobTitle())
                .eq(QuestionSetPo::getDirection, request.direction())
                .eq(QuestionSetPo::getLevel, request.level())
                .eq(QuestionSetPo::getCompanyStyle, request.companyStyle())
                .orderByDesc(QuestionSetPo::getCreatedAt)
                .last("LIMIT 1")
        );
        if (latest == null || latest.getQuestions() == null || latest.getQuestions().isEmpty()) {
            return null;
        }
        if (latest.getQuestions().size() != request.count()) {
            return null;
        }
        Set<String> requestedCategories = normalizeCategories(request.categories());
        if (!requestedCategories.isEmpty() && !requestedCategories.equals(questionCategories(latest.getQuestions()))) {
            return null;
        }
        return latest;
    }

    private Set<String> questionCategories(List<QuestionPo> questions) {
        return questions.stream()
            .map(QuestionPo::getCategory)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> normalizeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return Set.of();
        }
        return categories.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String requestSignature(QuestionGenerateRequest request) {
        String categories = String.join(",", normalizeCategories(request.categories()));
        String signature = String.join("|",
            String.valueOf(request.resumeId()),
            safe(request.jobTitle()),
            safe(request.direction()),
            safe(request.level()),
            safe(request.companyStyle()),
            String.valueOf(request.count()),
            categories
        );
        return DigestUtils.md5DigestAsHex(signature.getBytes(StandardCharsets.UTF_8));
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
