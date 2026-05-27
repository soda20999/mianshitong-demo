package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.common.constant.RedisKeys;
import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.bo.AiCallResult;
import com.mianshitong.project.entity.bo.ReportAiResult;
import com.mianshitong.project.entity.po.InterviewSessionPo;
import com.mianshitong.project.entity.po.QuestionPo;
import com.mianshitong.project.entity.po.QuestionSetPo;
import com.mianshitong.project.entity.po.ReportPo;
import com.mianshitong.project.enum_.TaskStatus;
import com.mianshitong.project.mapper.InterviewSessionMapper;
import com.mianshitong.project.mapper.QuestionSetMapper;
import com.mianshitong.project.mapper.ReportMapper;
import com.mianshitong.project.service.ReportService;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final QuestionSetMapper questionSetMapper;
    private final SpringAiEngine springAiEngine;
    private final AiLogSupport aiLogSupport;
    private final ReportPdfExporter reportPdfExporter;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisLockSupport redisLockSupport;

    @Value("${app.redis.report-task-status-ttl-hours:72}")
    private long reportTaskStatusTtlHours;

    @Value("${app.redis.report-queue-batch-size:3}")
    private int reportQueueBatchSize;

    @Value("${app.redis.report-queue-enabled:true}")
    private boolean reportQueueEnabled;

    private volatile long lastRedisUnavailableLogAt = 0L;

    @PostConstruct
    public void recoverPendingTasks() {
        if (!reportQueueEnabled) {
            return;
        }
        try {
            List<ReportPo> pendingReports = reportMapper.selectList(
                new LambdaQueryWrapper<ReportPo>()
                    .notIn(ReportPo::getStatus, TaskStatus.SUCCESS, TaskStatus.FAIL)
                    .orderByAsc(ReportPo::getCreatedAt)
            );
            for (ReportPo report : pendingReports) {
                enqueueReportTask(report.getId(), report.getStatus());
            }
        } catch (RedisConnectionFailureException ex) {
            logRedisUnavailable("recover pending report tasks", ex);
        }
    }

    @Scheduled(fixedDelayString = "${app.redis.report-queue-poll-ms:1000}")
    public void consumeReportQueue() {
        if (!reportQueueEnabled) {
            return;
        }
        try {
            int batchSize = Math.max(1, reportQueueBatchSize);
            for (int i = 0; i < batchSize; i++) {
                String reportIdRaw = stringRedisTemplate.opsForList().leftPop(RedisKeys.REPORT_TASK_QUEUE_KEY);
                if (!StringUtils.hasText(reportIdRaw)) {
                    return;
                }
                Long reportId = parseReportId(reportIdRaw);
                if (reportId == null) {
                    continue;
                }
                processReport(reportId);
            }
        } catch (RedisConnectionFailureException ex) {
            logRedisUnavailable("consume report queue", ex);
        }
    }

    @Override
    public List<ReportPo> listByUser(Long userId) {
        return reportMapper.selectList(
            new LambdaQueryWrapper<ReportPo>()
                .eq(ReportPo::getUserId, userId)
                .orderByDesc(ReportPo::getCreatedAt)
        ).stream().map(this::withLatestStatus).toList();
    }

    @Override
    public ReportPo getByUser(Long userId, Long reportId) {
        return withLatestStatus(requireOwnedReport(userId, reportId));
    }

    @Override
    public boolean favoriteQuestion(Long userId, Long reportId, Integer questionIndex) {
        ReportPo report = requireOwnedReport(userId, reportId);
        InterviewSessionPo session = requireOwnedSession(userId, report.getInterviewId());
        QuestionSetPo questionSet = questionSetMapper.selectById(session.getQuestionSetId());
        if (questionSet == null || questionSet.getQuestions() == null || questionSet.getQuestions().isEmpty()) {
            throw new BizException("未找到题集，无法收藏");
        }
        if (questionIndex == null || questionIndex < 0 || questionIndex >= questionSet.getQuestions().size()) {
            throw new BizException("题目序号不合法");
        }
        QuestionPo targetQuestion = questionSet.getQuestions().get(questionIndex);
        if (targetQuestion.getId() == null) {
            throw new BizException("题目数据异常，无法收藏");
        }

        Set<Long> favoriteIds = session.getFavoriteQuestionIds() == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(session.getFavoriteQuestionIds());
        boolean favorited;
        if (favoriteIds.contains(targetQuestion.getId())) {
            favoriteIds.remove(targetQuestion.getId());
            favorited = false;
        } else {
            favoriteIds.add(targetQuestion.getId());
            favorited = true;
        }
        session.setFavoriteQuestionIds(favoriteIds);
        interviewSessionMapper.updateById(session);
        return favorited;
    }

    @Override
    public QuestionSetPo redoWrongQuestions(Long userId, Long reportId) {
        ReportPo report = requireOwnedReport(userId, reportId);
        InterviewSessionPo session = requireOwnedSession(userId, report.getInterviewId());
        Set<Long> wrongIds = session.getWrongQuestionIds() == null ? Set.of() : session.getWrongQuestionIds();
        if (wrongIds.isEmpty()) {
            throw new BizException("当前报告暂无错题可重做");
        }

        QuestionSetPo originalSet = questionSetMapper.selectById(session.getQuestionSetId());
        if (originalSet == null || originalSet.getQuestions() == null || originalSet.getQuestions().isEmpty()) {
            throw new BizException("未找到原题集，无法重做错题");
        }

        List<QuestionPo> wrongQuestions = originalSet.getQuestions().stream()
            .filter(question -> question.getId() != null && wrongIds.contains(question.getId()))
            .toList();
        if (wrongQuestions.isEmpty()) {
            throw new BizException("当前报告暂无可重做错题");
        }

        QuestionSetPo retrySet = new QuestionSetPo();
        retrySet.setUserId(userId);
        retrySet.setResumeId(originalSet.getResumeId());
        retrySet.setJobTitle(originalSet.getJobTitle());
        retrySet.setDirection(originalSet.getDirection());
        retrySet.setLevel(originalSet.getLevel());
        retrySet.setCompanyStyle(originalSet.getCompanyStyle());
        retrySet.setQuestions(wrongQuestions);
        retrySet.setCreatedAt(LocalDateTime.now());
        questionSetMapper.insert(retrySet);
        return retrySet;
    }

    @Override
    public byte[] exportPdf(Long userId, Long reportId) {
        ReportPo report = requireOwnedReport(userId, reportId);
        validateExportable(report);
        return reportPdfExporter.export(report);
    }

    @Override
    public byte[] batchExportPdf(Long userId, List<Long> reportIds) {
        if (reportIds == null || reportIds.isEmpty()) {
            throw new BizException("请选择需要导出的报告");
        }
        List<Long> uniqueIds = reportIds.stream()
            .filter(Objects::nonNull)
            .distinct()
            .limit(50)
            .toList();
        if (uniqueIds.isEmpty()) {
            throw new BizException("请选择需要导出的报告");
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Long reportId : uniqueIds) {
                ReportPo report = requireOwnedReport(userId, reportId);
                validateExportable(report);
                byte[] pdfBytes = reportPdfExporter.export(report);
                String entryName = "report-" + reportId + ".pdf";
                zipOut.putNextEntry(new ZipEntry(entryName));
                zipOut.write(pdfBytes);
                zipOut.closeEntry();
            }
            zipOut.finish();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BizException("批量导出失败：" + ex.getMessage());
        }
    }

    public List<ReportPo> allReports() {
        return reportMapper.selectList(
            new LambdaQueryWrapper<ReportPo>()
                .orderByDesc(ReportPo::getCreatedAt)
        ).stream().map(this::withLatestStatus).toList();
    }

    private ReportPo requireOwnedReport(Long userId, Long reportId) {
        ReportPo report = reportMapper.selectOne(
            new LambdaQueryWrapper<ReportPo>()
                .eq(ReportPo::getId, reportId)
                .eq(ReportPo::getUserId, userId)
                .last("LIMIT 1")
        );
        if (report == null) {
            throw new BizException("报告不存在");
        }
        return report;
    }

    private InterviewSessionPo requireOwnedSession(Long userId, Long interviewId) {
        InterviewSessionPo session = interviewSessionMapper.selectOne(
            new LambdaQueryWrapper<InterviewSessionPo>()
                .eq(InterviewSessionPo::getId, interviewId)
                .eq(InterviewSessionPo::getUserId, userId)
                .last("LIMIT 1")
        );
        if (session == null) {
            throw new BizException("面试会话不存在");
        }
        return session;
    }

    private void validateExportable(ReportPo report) {
        if (report.getStatus() != TaskStatus.SUCCESS) {
            throw new BizException("报告尚未生成完成，暂不支持导出");
        }
    }

    private void processReport(Long reportId) {
        String lockKey = RedisKeys.reportTaskLock(reportId);
        String lockToken = redisLockSupport.tryLock(lockKey, Duration.ofMinutes(5));
        if (lockToken == null) {
            return;
        }
        try {
            ReportPo report = reportMapper.selectById(reportId);
            if (report == null) {
                stringRedisTemplate.delete(RedisKeys.reportTaskStatus(reportId));
                return;
            }
            if (report.getStatus() == TaskStatus.SUCCESS || report.getStatus() == TaskStatus.FAIL) {
                setTaskStatus(reportId, report.getStatus());
                return;
            }

            updateTaskStatus(report, TaskStatus.RUNNING);
            InterviewSessionPo session = interviewSessionMapper.selectById(report.getInterviewId());
            if (session == null) {
                updateTaskStatus(report, TaskStatus.FAIL);
                return;
            }
            QuestionSetPo questionSet = questionSetMapper.selectById(session.getQuestionSetId());
            List<QuestionPo> questions = questionSet == null || questionSet.getQuestions() == null
                ? List.of()
                : questionSet.getQuestions();

            try {
                AiCallResult<ReportAiResult> aiResult = springAiEngine.generateReport(
                    questionSet == null ? "" : questionSet.getJobTitle(),
                    session,
                    questions
                );
                ReportAiResult aiReport = aiResult.data();
                report.setOverallScore(aiReport.getOverallScore());
                report.setDimensions(aiReport.getDimensions());
                report.setWeakPoints(aiReport.getWeakPoints());
                report.setReviewRoadmap(aiReport.getReviewRoadmap());
                report.setQuestionList(aiReport.getQuestionList());
                report.setUserAnswerHighlights(aiReport.getUserAnswerHighlights());
                report.setAiStandardAnswers(aiReport.getAiStandardAnswers());
                report.setBrightSpots(aiReport.getBrightSpots());
                updateTaskStatus(report, TaskStatus.SUCCESS);
                aiLogSupport.log(report.getUserId(), "report", aiResult.usage(), "SUCCESS");
            } catch (Exception ex) {
                updateTaskStatus(report, TaskStatus.FAIL);
                aiLogSupport.log(report.getUserId(), "report", 0, 0, "FAIL");
            }
        } finally {
            redisLockSupport.unlock(lockKey, lockToken);
        }
    }

    private void updateTaskStatus(ReportPo report, TaskStatus status) {
        report.setStatus(status);
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
        setTaskStatus(report.getId(), status);
    }

    private void enqueueReportTask(Long reportId, TaskStatus status) {
        if (reportId == null) {
            return;
        }
        try {
            setTaskStatus(reportId, status == null ? TaskStatus.PENDING : status);
            stringRedisTemplate.opsForList().rightPush(RedisKeys.REPORT_TASK_QUEUE_KEY, String.valueOf(reportId));
        } catch (RedisConnectionFailureException ex) {
            logRedisUnavailable("enqueue report task", ex);
        }
    }

    private void setTaskStatus(Long reportId, TaskStatus status) {
        if (reportId == null || status == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                RedisKeys.reportTaskStatus(reportId),
                status.name(),
                Duration.ofHours(Math.max(1, reportTaskStatusTtlHours))
            );
        } catch (RedisConnectionFailureException ex) {
            logRedisUnavailable("update report task status", ex);
        }
    }

    private ReportPo withLatestStatus(ReportPo report) {
        if (report == null || report.getId() == null) {
            return report;
        }
        String redisStatusValue;
        try {
            redisStatusValue = stringRedisTemplate.opsForValue().get(RedisKeys.reportTaskStatus(report.getId()));
        } catch (RedisConnectionFailureException ex) {
            logRedisUnavailable("read report task status", ex);
            return report;
        }
        if (!StringUtils.hasText(redisStatusValue)
            && (report.getStatus() == TaskStatus.PENDING || report.getStatus() == TaskStatus.RUNNING)) {
            enqueueReportTask(report.getId(), report.getStatus());
            report.setStatus(report.getStatus());
            return report;
        }
        TaskStatus redisStatus = parseTaskStatus(redisStatusValue, report.getStatus());
        report.setStatus(redisStatus);
        return report;
    }

    private TaskStatus parseTaskStatus(String statusValue, TaskStatus fallback) {
        if (!StringUtils.hasText(statusValue)) {
            return fallback;
        }
        try {
            return TaskStatus.valueOf(statusValue.trim().toUpperCase());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Long parseReportId(String payload) {
        try {
            return Long.parseLong(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private void logRedisUnavailable(String action, Exception ex) {
        long now = System.currentTimeMillis();
        if (now - lastRedisUnavailableLogAt < 30_000) {
            return;
        }
        lastRedisUnavailableLogAt = now;
        log.warn("Redis unavailable, skip {}: {}", action, ex.getMessage());
    }
}
