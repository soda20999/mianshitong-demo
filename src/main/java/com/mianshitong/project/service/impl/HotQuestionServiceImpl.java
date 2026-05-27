package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.common.constant.RedisKeys;
import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.bo.AiCallResult;
import com.mianshitong.project.entity.po.HotQuestionFavoritePo;
import com.mianshitong.project.entity.po.HotQuestionPo;
import com.mianshitong.project.entity.po.InterviewMessagePo;
import com.mianshitong.project.entity.po.ScoreDetailPo;
import com.mianshitong.project.entity.vo.HotQuestionPracticeScoreVo;
import com.mianshitong.project.enum_.InterviewStyle;
import com.mianshitong.project.mapper.HotQuestionFavoriteMapper;
import com.mianshitong.project.mapper.HotQuestionMapper;
import com.mianshitong.project.service.HotQuestionService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class HotQuestionServiceImpl implements HotQuestionService {

    private static final int RANDOM_LIST_SIZE = 20;
    private static final int MAX_EXTRACTED_QUESTIONS = 500;
    private static final int MAX_AI_EXTRACT_COUNT = 180;
    private static final String DEFAULT_TAG = "\u7efc\u5408";
    private static final String UNKNOWN_ANSWER = "\u9898\u5e93\u539f\u6587\u672a\u63d0\u4f9b\u660e\u786e\u7b54\u6848";
    private static final Pattern POSITION_PATTERN = Pattern.compile(
        "(?im)^(?:岗位|职位|应聘岗位|目标岗位|求职岗位|面试岗位)\\s*[:：]\\s*(.+)$"
    );
    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s*(.+)$");
    private static final Pattern BRACKET_HEADING_PATTERN = Pattern.compile("^(?:\\[([^\\]]{1,40})]|【([^】]{1,40})】)$");
    private static final Pattern INLINE_TAG_PATTERN = Pattern.compile("^(?:\\[([^\\]]{1,40})]|【([^】]{1,40})】)\\s*(.+)$");
    private static final Pattern NUMBERED_TITLE_PATTERN = Pattern.compile("^\\d+[\\.、\\)]\\s*(.{2,80})$");
    private static final Pattern COLON_TITLE_PATTERN = Pattern.compile("^(.{2,80})[：:]$");
    private static final Pattern SHORT_TITLE_PATTERN = Pattern.compile("^[A-Za-z0-9\\u4e00-\\u9fa5][A-Za-z0-9\\u4e00-\\u9fa5\\s\\-_/（）()]{1,40}$");
    private static final Pattern QUESTION_PATTERN = Pattern.compile(
        "^(?:[-*•]?\\s*|\\d+[\\.、\\)]\\s*)?(?:问题|问|Q|Question)\\s*\\d*\\s*[：:]\\s*(.+)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ANSWER_PATTERN = Pattern.compile(
        "^(?:[-*•]?\\s*|\\d+[\\.、\\)]\\s*)?(?:答案|答|A|Answer|参考答案|标准答案|解析)\\s*\\d*\\s*[：:]\\s*(.*)$",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TAG_LINE_PATTERN = Pattern.compile(
        "(?i)^(?:tag|label|topic|\\u6807\\u7b7e|\\u5206\\u7c7b|\\u77e5\\u8bc6\\u70b9)\\s*[:\uff1a]\\s*(.{1,40})$"
    );
    private static final Pattern INLINE_QA_PATTERN = Pattern.compile(
        "(?is)^(?:q(?:uestion)?|\\u95ee\\u9898)\\s*[:\uff1a]\\s*(.+?)\\s*"
            + "(?:a(?:nswer)?|\\u7b54\\u6848|\\u53c2\\u8003\\u7b54\\u6848|\\u6807\\u51c6\\u7b54\\u6848|\\u89e3\\u6790)\\s*[:\uff1a]\\s*(.+)$"
    );

    private final HotQuestionMapper hotQuestionMapper;
    private final HotQuestionFavoriteMapper hotQuestionFavoriteMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SpringAiEngine springAiEngine;
    private final AiLogSupport aiLogSupport;
    private final AiRateLimitSupport aiRateLimitSupport;

    @Override
    public List<HotQuestionPo> list(Long userId, String tag, String position) {
        requireUser(userId);
        ensureHotIndex();

        List<HotQuestionPo> questions = hotQuestionMapper.selectList(
            new LambdaQueryWrapper<HotQuestionPo>()
                .eq(HotQuestionPo::getUserId, userId)
        ).stream()
            .peek(this::syncQuestionIfAbsent)
            .map(this::applyMetricsFromRedis)
            .filter(item -> matchTag(item, tag))
            .filter(item -> matchPosition(item, position))
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        if (questions.isEmpty()) {
            return List.of();
        }

        Collections.shuffle(questions);
        List<HotQuestionPo> sampled = questions.size() > RANDOM_LIST_SIZE
            ? new ArrayList<>(questions.subList(0, RANDOM_LIST_SIZE))
            : questions;
        sampled.sort(Comparator.comparingDouble(this::rankingScore).reversed());
        enrichFavoriteState(userId, sampled);
        return sampled;
    }

    @Override
    public HotQuestionPo detail(Long userId, Long id, boolean recordView) {
        requireUser(userId);
        HotQuestionPo question = recordView
            ? action(userId, id, "VIEW")
            : requireOwnedQuestion(userId, id);
        if (!recordView) {
            syncQuestionIfAbsent(question);
            question = applyMetricsFromRedis(question);
            question.setFavorited(isFavorited(userId, id));
        }
        return question;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HotQuestionPo action(Long userId, Long id, String action) {
        requireUser(userId);
        if (!StringUtils.hasText(action)) {
            throw new BizException("操作类型不能为空");
        }

        String normalized = action.trim().toUpperCase(Locale.ROOT);
        if ("FAVORITE".equals(normalized) || "UNFAVORITE".equals(normalized)) {
            return favorite(userId, id, "FAVORITE".equals(normalized));
        }

        HotQuestionPo question = requireOwnedQuestion(userId, id);
        syncQuestionIfAbsent(question);
        String idKey = String.valueOf(id);
        long views = readMetric(RedisKeys.HOT_VIEW_HASH_KEY, idKey, question.getViews());
        long favorites = readMetric(RedisKeys.HOT_FAVORITE_HASH_KEY, idKey, question.getFavorites());
        long practices = readMetric(RedisKeys.HOT_PRACTICE_HASH_KEY, idKey, question.getPractices());

        switch (normalized) {
            case "VIEW" -> views = incrementMetric(RedisKeys.HOT_VIEW_HASH_KEY, idKey, views);
            case "PRACTICE" -> practices = incrementMetric(RedisKeys.HOT_PRACTICE_HASH_KEY, idKey, practices);
            default -> throw new BizException("不支持的操作");
        }

        question.setViews(views);
        question.setFavorites(favorites);
        question.setPractices(practices);
        hotQuestionMapper.updateById(question);
        updateRankingScore(question);
        question.setFavorited(isFavorited(userId, id));
        return question;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HotQuestionPo favorite(Long userId, Long id, boolean favorite) {
        requireUser(userId);
        HotQuestionPo question = requireOwnedQuestion(userId, id);
        syncQuestionIfAbsent(question);

        boolean alreadyFavorited = isFavorited(userId, id);
        if (favorite == alreadyFavorited) {
            HotQuestionPo latest = applyMetricsFromRedis(question);
            latest.setFavorited(alreadyFavorited);
            return latest;
        }

        if (favorite) {
            HotQuestionFavoritePo record = new HotQuestionFavoritePo();
            record.setUserId(userId);
            record.setQuestionId(id);
            record.setCreatedAt(LocalDateTime.now());
            hotQuestionFavoriteMapper.insert(record);
        } else {
            hotQuestionFavoriteMapper.delete(
                new LambdaQueryWrapper<HotQuestionFavoritePo>()
                    .eq(HotQuestionFavoritePo::getUserId, userId)
                    .eq(HotQuestionFavoritePo::getQuestionId, id)
            );
        }

        String idKey = String.valueOf(id);
        long views = readMetric(RedisKeys.HOT_VIEW_HASH_KEY, idKey, question.getViews());
        long favorites = readMetric(RedisKeys.HOT_FAVORITE_HASH_KEY, idKey, question.getFavorites());
        long practices = readMetric(RedisKeys.HOT_PRACTICE_HASH_KEY, idKey, question.getPractices());

        favorites = favorite ? favorites + 1 : Math.max(0, favorites - 1);
        setMetric(RedisKeys.HOT_FAVORITE_HASH_KEY, idKey, favorites);

        question.setViews(views);
        question.setFavorites(favorites);
        question.setPractices(practices);
        hotQuestionMapper.updateById(question);
        updateRankingScore(question);
        question.setFavorited(favorite);
        return question;
    }

    @Override
    public HotQuestionPracticeScoreVo scorePractice(Long userId, Long id, String answer) {
        requireUser(userId);
        if (!StringUtils.hasText(answer)) {
            throw new BizException("请输入回答内容");
        }
        aiRateLimitSupport.checkPerMinuteLimit(userId);
        HotQuestionPo question = requireOwnedQuestion(userId, id);

        AiCallResult<ScoreDetailPo> scoreResult = springAiEngine.scoreAnswer(
            InterviewStyle.FOLLOW_UP,
            question.getContent(),
            answer.trim(),
            List.<InterviewMessagePo>of()
        );
        aiLogSupport.log(userId, "hot-practice-score", scoreResult.usage(), "SUCCESS");

        syncQuestionIfAbsent(question);
        question = applyMetricsFromRedis(question);
        question.setFavorited(isFavorited(userId, id));
        String correctAnswer = StringUtils.hasText(question.getAnswer()) ? question.getAnswer().trim() : "暂无标准答案";
        return new HotQuestionPracticeScoreVo(question, scoreResult.data(), correctAnswer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rebuildFromQuestionBank(Long userId, Long bankFileId, String fileName, String content, String defaultPosition) {
        requireUser(userId);
        if (bankFileId == null || bankFileId <= 0) {
            throw new BizException("题库文件参数不合法");
        }

        removeByQuestionBank(userId, bankFileId);
        ExtractionResult extraction = extractFromBank(userId, content, fileName, defaultPosition);
        if (extraction.items().isEmpty()) {
            throw new BizException("未识别到可用问题，请补充更清晰的题库内容后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        List<ExtractedQa> items = extraction.items().size() > MAX_EXTRACTED_QUESTIONS
            ? extraction.items().subList(0, MAX_EXTRACTED_QUESTIONS)
            : extraction.items();
        for (ExtractedQa item : items) {
            HotQuestionPo question = new HotQuestionPo();
            question.setUserId(userId);
            question.setBankFileId(bankFileId);
            question.setPosition(extraction.position());
            question.setTag(item.tag());
            question.setContent(item.question());
            question.setAnswer(item.answer());
            question.setViews(0L);
            question.setFavorites(0L);
            question.setPractices(0L);
            question.setCreatedAt(now);
            hotQuestionMapper.insert(question);
            syncQuestionIfAbsent(question);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByQuestionBank(Long userId, Long bankFileId) {
        if (bankFileId == null || bankFileId <= 0) {
            return;
        }
        removeByQuestionBanks(userId, List.of(bankFileId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByQuestionBanks(Long userId, List<Long> bankFileIds) {
        requireUser(userId);
        if (bankFileIds == null || bankFileIds.isEmpty()) {
            return;
        }
        List<Long> ids = bankFileIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }

        List<HotQuestionPo> questions = hotQuestionMapper.selectList(
            new LambdaQueryWrapper<HotQuestionPo>()
                .select(HotQuestionPo::getId)
                .eq(HotQuestionPo::getUserId, userId)
                .in(HotQuestionPo::getBankFileId, ids)
        );
        if (questions.isEmpty()) {
            return;
        }
        List<Long> questionIds = questions.stream().map(HotQuestionPo::getId).filter(Objects::nonNull).toList();
        if (questionIds.isEmpty()) {
            return;
        }

        hotQuestionFavoriteMapper.delete(
            new LambdaQueryWrapper<HotQuestionFavoritePo>()
                .in(HotQuestionFavoritePo::getQuestionId, questionIds)
        );
        hotQuestionMapper.delete(
            new LambdaQueryWrapper<HotQuestionPo>()
                .eq(HotQuestionPo::getUserId, userId)
                .in(HotQuestionPo::getId, questionIds)
        );
        removeMetrics(questionIds);
    }

    private void ensureHotIndex() {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeys.HOT_INDEX_READY_KEY))) {
            return;
        }
        synchronized (this) {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeys.HOT_INDEX_READY_KEY))) {
                return;
            }
            List<HotQuestionPo> questions = hotQuestionMapper.selectList(new LambdaQueryWrapper<>());
            questions.forEach(this::syncQuestionIfAbsent);
            stringRedisTemplate.opsForValue().set(RedisKeys.HOT_INDEX_READY_KEY, "1");
        }
    }

    private HotQuestionPo requireOwnedQuestion(Long userId, Long id) {
        if (id == null || id <= 0) {
            throw new BizException("问题参数不合法");
        }
        HotQuestionPo question = hotQuestionMapper.selectOne(
            new LambdaQueryWrapper<HotQuestionPo>()
                .eq(HotQuestionPo::getId, id)
                .eq(HotQuestionPo::getUserId, userId)
                .last("LIMIT 1")
        );
        if (question == null) {
            throw new BizException("问题不存在");
        }
        return question;
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException("请先登录");
        }
    }

    private boolean matchTag(HotQuestionPo question, String tag) {
        if (!StringUtils.hasText(tag)) {
            return true;
        }
        String source = question == null ? "" : question.getTag();
        return StringUtils.hasText(source) && source.equalsIgnoreCase(tag.trim());
    }

    private boolean matchPosition(HotQuestionPo question, String position) {
        if (!StringUtils.hasText(position)) {
            return true;
        }
        String source = question == null ? "" : question.getPosition();
        return StringUtils.hasText(source) && source.toLowerCase(Locale.ROOT).contains(position.trim().toLowerCase(Locale.ROOT));
    }

    private void enrichFavoriteState(Long userId, List<HotQuestionPo> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        List<Long> ids = questions.stream().map(HotQuestionPo::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        Set<Long> favoriteIds = hotQuestionFavoriteMapper.selectList(
            new LambdaQueryWrapper<HotQuestionFavoritePo>()
                .select(HotQuestionFavoritePo::getQuestionId)
                .eq(HotQuestionFavoritePo::getUserId, userId)
                .in(HotQuestionFavoritePo::getQuestionId, ids)
        ).stream().map(HotQuestionFavoritePo::getQuestionId).collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        for (HotQuestionPo question : questions) {
            question.setFavorited(question.getId() != null && favoriteIds.contains(question.getId()));
        }
    }

    private boolean isFavorited(Long userId, Long questionId) {
        if (userId == null || questionId == null) {
            return false;
        }
        return hotQuestionFavoriteMapper.selectCount(
            new LambdaQueryWrapper<HotQuestionFavoritePo>()
                .eq(HotQuestionFavoritePo::getUserId, userId)
                .eq(HotQuestionFavoritePo::getQuestionId, questionId)
        ) > 0;
    }

    private void syncQuestionIfAbsent(HotQuestionPo question) {
        if (question == null || question.getId() == null) {
            return;
        }
        String idKey = String.valueOf(question.getId());
        stringRedisTemplate.opsForHash().putIfAbsent(RedisKeys.HOT_VIEW_HASH_KEY, idKey, String.valueOf(defaultLong(question.getViews())));
        stringRedisTemplate.opsForHash().putIfAbsent(RedisKeys.HOT_FAVORITE_HASH_KEY, idKey, String.valueOf(defaultLong(question.getFavorites())));
        stringRedisTemplate.opsForHash().putIfAbsent(RedisKeys.HOT_PRACTICE_HASH_KEY, idKey, String.valueOf(defaultLong(question.getPractices())));
        if (stringRedisTemplate.opsForZSet().score(RedisKeys.HOT_RANKING_ZSET_KEY, idKey) == null) {
            stringRedisTemplate.opsForZSet().add(RedisKeys.HOT_RANKING_ZSET_KEY, idKey, heatScore(question));
        }
    }

    private HotQuestionPo applyMetricsFromRedis(HotQuestionPo question) {
        if (question == null || question.getId() == null) {
            return question;
        }
        String idKey = String.valueOf(question.getId());
        question.setViews(readMetric(RedisKeys.HOT_VIEW_HASH_KEY, idKey, question.getViews()));
        question.setFavorites(readMetric(RedisKeys.HOT_FAVORITE_HASH_KEY, idKey, question.getFavorites()));
        question.setPractices(readMetric(RedisKeys.HOT_PRACTICE_HASH_KEY, idKey, question.getPractices()));
        return question;
    }

    private double rankingScore(HotQuestionPo question) {
        if (question == null || question.getId() == null) {
            return 0D;
        }
        Double score = stringRedisTemplate.opsForZSet().score(RedisKeys.HOT_RANKING_ZSET_KEY, String.valueOf(question.getId()));
        return score == null ? heatScore(question) : score;
    }

    private long incrementMetric(String hashKey, String idKey, long fallback) {
        Long value = stringRedisTemplate.opsForHash().increment(hashKey, idKey, 1L);
        return value == null ? fallback + 1L : Math.max(0, value);
    }

    private void setMetric(String hashKey, String idKey, long value) {
        stringRedisTemplate.opsForHash().put(hashKey, idKey, String.valueOf(Math.max(0, value)));
    }

    private long readMetric(String hashKey, String idKey, Long fallback) {
        Object value = stringRedisTemplate.opsForHash().get(hashKey, idKey);
        if (value == null) {
            return defaultLong(fallback);
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return defaultLong(fallback);
        }
    }

    private void updateRankingScore(HotQuestionPo question) {
        if (question == null || question.getId() == null) {
            return;
        }
        String idKey = String.valueOf(question.getId());
        stringRedisTemplate.opsForZSet().add(RedisKeys.HOT_RANKING_ZSET_KEY, idKey, heatScore(question));
    }

    private void removeMetrics(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return;
        }
        Object[] idKeys = questionIds.stream().filter(Objects::nonNull).map(String::valueOf).toArray();
        if (idKeys.length == 0) {
            return;
        }
        stringRedisTemplate.opsForHash().delete(RedisKeys.HOT_VIEW_HASH_KEY, idKeys);
        stringRedisTemplate.opsForHash().delete(RedisKeys.HOT_FAVORITE_HASH_KEY, idKeys);
        stringRedisTemplate.opsForHash().delete(RedisKeys.HOT_PRACTICE_HASH_KEY, idKeys);
        stringRedisTemplate.opsForZSet().remove(RedisKeys.HOT_RANKING_ZSET_KEY, idKeys);
    }

    private long heatScore(HotQuestionPo question) {
        long views = question == null || question.getViews() == null ? 0L : question.getViews();
        long favorites = question == null || question.getFavorites() == null ? 0L : question.getFavorites();
        long practices = question == null || question.getPractices() == null ? 0L : question.getPractices();
        return views + favorites * 2 + practices * 3;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private ExtractionResult extractFromBank(Long userId, String content, String fileName, String defaultPosition) {
        String normalized = normalizeMultiline(content);
        if (!StringUtils.hasText(normalized)) {
            return new ExtractionResult(resolvePosition("", fileName, defaultPosition), List.of());
        }
        String position = resolvePosition(normalized, fileName, defaultPosition);
        // Accuracy-first strategy: use model extraction only and do not mix rule-based parsing.
        int aiCount = Math.min(MAX_EXTRACTED_QUESTIONS, Math.max(MAX_AI_EXTRACT_COUNT, 120));
        AiExtractResult aiExtractResult = parseByAi(userId, normalized, fileName, position, aiCount);
        if (StringUtils.hasText(aiExtractResult.position())) {
            position = aiExtractResult.position();
        }
        List<ExtractedQa> items = aiExtractResult.items() == null ? List.of() : aiExtractResult.items();
        return new ExtractionResult(position, items);
    }

    private String resolvePosition(String content, String fileName, String defaultPosition) {
        Matcher matcher = POSITION_PATTERN.matcher(content == null ? "" : content);
        if (matcher.find()) {
            String extracted = sanitizePosition(matcher.group(1));
            if (StringUtils.hasText(extracted)) {
                return extracted;
            }
        }
        if (StringUtils.hasText(defaultPosition)) {
            return sanitizePosition(defaultPosition);
        }
        String fromFileName = sanitizePosition(extractFromFileName(fileName));
        if (StringUtils.hasText(fromFileName)) {
            return fromFileName;
        }
        return "通用岗位";
    }

    private String extractFromFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String safe = fileName.replace("\\", "/");
        safe = safe.substring(safe.lastIndexOf('/') + 1);
        int dot = safe.lastIndexOf('.');
        if (dot > 0) {
            safe = safe.substring(0, dot);
        }
        safe = safe
            .replaceAll("(?i)(题库|面试|问题|question|bank|整理|汇总|合集|大全|最新版|final)", " ")
            .replaceAll("[-_]+", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
        return safe;
    }

    private List<ExtractedQa> parseQaPairs(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String[] lines = content.replace('\r', '\n').split("\n");
        List<ExtractedQa> result = new ArrayList<>();
        Set<String> deduplicated = new LinkedHashSet<>();

        String currentTag = DEFAULT_TAG;
        String currentQuestionTag = currentTag;
        StringBuilder questionBuffer = null;
        StringBuilder answerBuffer = null;
        boolean collectingQuestion = false;
        boolean collectingAnswer = false;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!StringUtils.hasText(line)) {
                if (collectingQuestion && questionBuffer != null && questionBuffer.length() > 0) {
                    questionBuffer.append('\n');
                }
                if (collectingAnswer && answerBuffer != null && answerBuffer.length() > 0) {
                    answerBuffer.append('\n');
                }
                continue;
            }

            String headingTag = extractHeadingTag(line);
            if (StringUtils.hasText(headingTag) && !collectingQuestion && !collectingAnswer) {
                currentTag = sanitizeTag(headingTag);
                continue;
            }
            Matcher tagMatcher = TAG_LINE_PATTERN.matcher(line);
            if (tagMatcher.matches() && !collectingQuestion && !collectingAnswer) {
                currentTag = sanitizeTag(tagMatcher.group(1));
                continue;
            }

            String lineTag = "";
            String body = line;
            Matcher inlineTag = INLINE_TAG_PATTERN.matcher(line);
            if (inlineTag.matches()) {
                lineTag = firstNotBlank(inlineTag.group(1), inlineTag.group(2));
                body = inlineTag.group(3).trim();
            }

            Matcher inlineQa = INLINE_QA_PATTERN.matcher(body);
            if (inlineQa.matches()) {
                if (collectingAnswer) {
                    appendExtracted(result, deduplicated, currentQuestionTag, questionBuffer, answerBuffer);
                } else if (collectingQuestion) {
                    appendExtractedWithDefaultAnswer(result, deduplicated, currentQuestionTag, questionBuffer);
                }
                String tag = StringUtils.hasText(lineTag) ? sanitizeTag(lineTag) : currentTag;
                appendExtracted(
                    result,
                    deduplicated,
                    tag,
                    new StringBuilder(inlineQa.group(1)),
                    new StringBuilder(inlineQa.group(2))
                );
                questionBuffer = null;
                answerBuffer = null;
                collectingQuestion = false;
                collectingAnswer = false;
                continue;
            }

            Matcher questionMatcher = QUESTION_PATTERN.matcher(body);
            if (questionMatcher.matches()) {
                if (collectingAnswer) {
                    appendExtracted(result, deduplicated, currentQuestionTag, questionBuffer, answerBuffer);
                } else if (collectingQuestion) {
                    appendExtractedWithDefaultAnswer(result, deduplicated, currentQuestionTag, questionBuffer);
                }
                questionBuffer = new StringBuilder();
                answerBuffer = new StringBuilder();
                appendLine(questionBuffer, questionMatcher.group(1));
                currentQuestionTag = StringUtils.hasText(lineTag) ? sanitizeTag(lineTag) : currentTag;
                collectingQuestion = true;
                collectingAnswer = false;
                continue;
            }

            Matcher answerMatcher = ANSWER_PATTERN.matcher(body);
            if (answerMatcher.matches()) {
                if (!collectingQuestion || questionBuffer == null || questionBuffer.length() == 0) {
                    continue;
                }
                collectingQuestion = false;
                collectingAnswer = true;
                appendLine(answerBuffer, answerMatcher.group(1));
                continue;
            }

            if (collectingAnswer) {
                appendLine(answerBuffer, body);
            } else if (collectingQuestion) {
                appendLine(questionBuffer, body);
            }
        }

        if (collectingAnswer) {
            appendExtracted(result, deduplicated, currentQuestionTag, questionBuffer, answerBuffer);
        } else if (collectingQuestion) {
            appendExtractedWithDefaultAnswer(result, deduplicated, currentQuestionTag, questionBuffer);
        }
        return result;
    }

    private List<ExtractedQa> parseByTitleSections(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        String[] lines = content.replace('\r', '\n').split("\n");
        List<ExtractedQa> result = new ArrayList<>();
        Set<String> deduplicated = new LinkedHashSet<>();

        String currentTitle = "";
        StringBuilder currentBody = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (!StringUtils.hasText(line)) {
                if (currentBody.length() > 0) {
                    currentBody.append('\n');
                }
                continue;
            }

            String nextNonBlank = nextNonBlankLine(lines, i + 1);
            String title = detectSectionTitle(line, nextNonBlank);
            if (StringUtils.hasText(title)) {
                appendSectionExtracted(result, deduplicated, currentTitle, currentBody);
                currentTitle = title;
                currentBody = new StringBuilder();
                continue;
            }

            if (StringUtils.hasText(currentTitle)) {
                appendLine(currentBody, line);
            }
        }
        appendSectionExtracted(result, deduplicated, currentTitle, currentBody);
        return result;
    }

    private String nextNonBlankLine(String[] lines, int startIndex) {
        if (lines == null || startIndex >= lines.length) {
            return "";
        }
        for (int i = Math.max(0, startIndex); i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (StringUtils.hasText(line)) {
                return line;
            }
        }
        return "";
    }

    private String detectSectionTitle(String line, String nextNonBlankLine) {
        if (!StringUtils.hasText(line) || !StringUtils.hasText(nextNonBlankLine)) {
            return "";
        }
        if (QUESTION_PATTERN.matcher(line).matches() || ANSWER_PATTERN.matcher(line).matches()) {
            return "";
        }
        if (line.length() > 120) {
            return "";
        }

        String headingTag = extractHeadingTag(line);
        if (StringUtils.hasText(headingTag)) {
            return normalizeSingleLine(headingTag, 120);
        }
        Matcher numbered = NUMBERED_TITLE_PATTERN.matcher(line);
        if (numbered.matches()) {
            return normalizeSingleLine(numbered.group(1), 120);
        }
        Matcher colon = COLON_TITLE_PATTERN.matcher(line);
        if (colon.matches()) {
            return normalizeSingleLine(colon.group(1), 120);
        }
        if (line.startsWith("-") || line.startsWith("*") || line.startsWith("•")) {
            return "";
        }
        if (line.matches(".*[。！？?!；;].*") || line.contains("，")) {
            return "";
        }
        if (line.length() <= 42 && SHORT_TITLE_PATTERN.matcher(line).matches()) {
            return normalizeSingleLine(line, 120);
        }
        return "";
    }

    private void appendSectionExtracted(
        List<ExtractedQa> result,
        Set<String> deduplicated,
        String title,
        StringBuilder bodyBuffer
    ) {
        String question = normalizeBlock(title);
        String answer = normalizeBlock(bodyBuffer == null ? "" : bodyBuffer.toString());
        if (!StringUtils.hasText(question)) {
            return;
        }
        if (!StringUtils.hasText(answer)) {
            answer = UNKNOWN_ANSWER;
        }
        String uniqueKey = question + "\n---\n" + answer;
        if (!deduplicated.add(uniqueKey)) {
            return;
        }
        result.add(new ExtractedQa(resolveTag(guessTagFromTitle(question), question, answer), question, answer));
    }

    private String guessTagFromTitle(String title) {
        String safe = normalizeSingleLine(title, 40);
        if (!StringUtils.hasText(safe)) {
            return DEFAULT_TAG;
        }
        if (safe.length() <= 16) {
            return safe;
        }
        String[] separators = new String[]{"：", ":", "-", "—", "/", "|", " "};
        for (String separator : separators) {
            int idx = safe.indexOf(separator);
            if (idx <= 0) {
                continue;
            }
            String segment = normalizeSingleLine(safe.substring(0, idx), 20);
            if (StringUtils.hasText(segment)) {
                return segment;
            }
        }
        return DEFAULT_TAG;
    }

    private AiExtractResult parseByAi(Long userId, String content, String fileName, String positionHint, int maxCount) {
        aiRateLimitSupport.checkPerMinuteLimit(userId);
        int safeMaxCount = Math.max(1, Math.min(maxCount, MAX_EXTRACTED_QUESTIONS));
        AiCallResult<SpringAiEngine.QuestionBankExtractPayload> result = springAiEngine.extractQuestionBankQa(
            fileName,
            positionHint,
            content,
            safeMaxCount
        );
        aiLogSupport.log(userId, "hot-question-extract", result.usage(), "SUCCESS");

        SpringAiEngine.QuestionBankExtractPayload payload = result.data();
        if (payload == null || payload.getItems() == null || payload.getItems().isEmpty()) {
            return new AiExtractResult(positionHint, List.of());
        }
        List<ExtractedQa> extracted = new ArrayList<>();
        for (SpringAiEngine.QuestionBankExtractItem item : payload.getItems()) {
            if (item == null) {
                continue;
            }
            String question = normalizeBlock(item.getQuestion());
            String answer = normalizeBlock(item.getAnswer());
            if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
                continue;
            }
            extracted.add(new ExtractedQa(sanitizeTag(item.getTag()), question, answer));
        }
        return new AiExtractResult(sanitizePosition(payload.getPosition()), distinctExtracted(extracted));
    }

    private List<ExtractedQa> distinctExtracted(List<ExtractedQa> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<ExtractedQa> result = new ArrayList<>();
        Set<String> deduplicated = new LinkedHashSet<>();
        for (ExtractedQa item : source) {
            if (item == null) {
                continue;
            }
            String question = normalizeBlock(item.question());
            String answer = normalizeBlock(item.answer());
            if (!StringUtils.hasText(question)) {
                continue;
            }
            if (!StringUtils.hasText(answer)) {
                answer = UNKNOWN_ANSWER;
            }
            String tag = sanitizeTag(item.tag());
            String key = normalizeForMatch(question) + "\n---\n" + normalizeForMatch(answer);
            if (!deduplicated.add(key)) {
                continue;
            }
            result.add(new ExtractedQa(tag, question, answer));
        }
        return result;
    }

    private String resolveTag(String candidateTag, String question, String answer) {
        String safeTag = sanitizeTag(candidateTag);
        if (isInformativeTag(safeTag)) {
            return safeTag;
        }
        return inferTag(question, answer);
    }

    private boolean isInformativeTag(String tag) {
        if (!StringUtils.hasText(tag)) {
            return false;
        }
        String normalized = normalizeForMatch(tag);
        if (!StringUtils.hasText(normalized) || normalized.length() > 18) {
            return false;
        }
        return !normalized.equals(normalizeForMatch(DEFAULT_TAG))
            && !normalized.equals("\u901a\u7528")
            && !normalized.equals("\u9898\u5e93")
            && !normalized.equals("\u9762\u8bd5")
            && !normalized.equals("\u5176\u4ed6");
    }

    private String inferTag(String question, String answer) {
        String text = normalizeForMatch(question + "\n" + answer);
        if (!StringUtils.hasText(text)) {
            return DEFAULT_TAG;
        }
        if (containsAny(text, "redis", "\u7f13\u5b58", "\u5206\u5e03\u5f0f\u9501", "\u70ed\u70b9")) {
            return "Redis";
        }
        if (containsAny(text, "mysql", "sql", "\u7d22\u5f15", "\u4e8b\u52a1", "\u6162\u67e5\u8be2")) {
            return "MySQL";
        }
        if (containsAny(text, "jvm", "gc", "\u5185\u5b58", "\u5783\u573e\u56de\u6536", "\u7c7b\u52a0\u8f7d")) {
            return "JVM";
        }
        if (containsAny(text, "springboot", "spring", "mybatis", "\u5fae\u670d\u52a1")) {
            return "Spring";
        }
        if (containsAny(text, "kafka", "rocketmq", "rabbitmq", "mq", "\u6d88\u606f\u961f\u5217")) {
            return "MQ";
        }
        if (containsAny(text, "linux", "shell", "bash", "\u8fd0\u7ef4", "\u811a\u672c")) {
            return "Linux";
        }
        if (containsAny(text, "tcp", "udp", "http", "https", "\u7f51\u7edc", "\u534f\u8bae")) {
            return "Network";
        }
        if (containsAny(text, "algorithm", "leetcode", "\u7b97\u6cd5", "\u6570\u636e\u7ed3\u6784", "\u94fe\u8868", "\u6811")) {
            return "Algorithm";
        }
        if (containsAny(text, "\u7cfb\u7edf\u8bbe\u8ba1", "\u67b6\u6784", "\u5206\u5e03\u5f0f", "\u9ad8\u5e76\u53d1", "\u9650\u6d41")) {
            return "\u7cfb\u7edf\u8bbe\u8ba1";
        }
        if (containsAny(text, "\u9879\u76ee", "\u5b9e\u6218", "\u590d\u76d8", "\u6392\u969c", "\u4f18\u5316")) {
            return "\u9879\u76ee\u7ecf\u9a8c";
        }
        if (containsAny(text, "java", "\u5e76\u53d1", "\u591a\u7ebf\u7a0b", "\u7ebf\u7a0b\u6c60")) {
            return "Java";
        }
        return DEFAULT_TAG;
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            if (text.contains(normalizeForMatch(keyword))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForMatch(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
            .replace('\u3000', ' ')
            .replaceAll("[\\s\\p{Punct}\\u3000-\\u303F\\uFF00-\\uFF65]+", "");
    }

    private boolean isSupportedBySource(String sourceForMatch, String candidate) {
        if (!StringUtils.hasText(sourceForMatch)) {
            return true;
        }
        String normalizedCandidate = normalizeForMatch(candidate);
        if (!StringUtils.hasText(normalizedCandidate)) {
            return false;
        }
        if (normalizedCandidate.length() <= 6) {
            return sourceForMatch.contains(normalizedCandidate);
        }
        if (sourceForMatch.contains(normalizedCandidate)) {
            return true;
        }
        for (String fragment : extractEvidenceFragments(normalizedCandidate)) {
            if (sourceForMatch.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private List<String> extractEvidenceFragments(String normalizedCandidate) {
        if (!StringUtils.hasText(normalizedCandidate)) {
            return List.of();
        }
        int length = normalizedCandidate.length();
        int window = Math.min(24, Math.max(8, length / 3));
        int step = Math.max(4, window / 2);
        List<String> fragments = new ArrayList<>();
        for (int start = 0; start + window <= length; start += step) {
            fragments.add(normalizedCandidate.substring(start, start + window));
            if (fragments.size() >= 8) {
                break;
            }
        }
        if (fragments.isEmpty()) {
            fragments.add(normalizedCandidate.substring(0, Math.min(window, length)));
        }
        return fragments;
    }

    private String extractHeadingTag(String line) {
        Matcher markdown = HEADING_PATTERN.matcher(line);
        if (markdown.matches()) {
            return markdown.group(1);
        }
        Matcher bracket = BRACKET_HEADING_PATTERN.matcher(line);
        if (bracket.matches()) {
            return firstNotBlank(bracket.group(1), bracket.group(2));
        }
        return "";
    }

    private void appendLine(StringBuilder builder, String text) {
        if (builder == null || !StringUtils.hasText(text)) {
            return;
        }
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
        builder.append(text.trim());
    }

    private void appendExtractedWithDefaultAnswer(
        List<ExtractedQa> result,
        Set<String> deduplicated,
        String tag,
        StringBuilder questionBuffer
    ) {
        appendExtracted(result, deduplicated, tag, questionBuffer, new StringBuilder(UNKNOWN_ANSWER));
    }

    private void appendExtracted(
        List<ExtractedQa> result,
        Set<String> deduplicated,
        String tag,
        StringBuilder questionBuffer,
        StringBuilder answerBuffer
    ) {
        String question = normalizeBlock(questionBuffer == null ? "" : questionBuffer.toString());
        String answer = normalizeBlock(answerBuffer == null ? "" : answerBuffer.toString());
        if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
            return;
        }
        String uniqueKey = question + "\n---\n" + answer;
        if (!deduplicated.add(uniqueKey)) {
            return;
        }
        result.add(new ExtractedQa(sanitizeTag(tag), question, answer));
    }

    private String sanitizePosition(String position) {
        String safe = normalizeSingleLine(position, 128);
        return StringUtils.hasText(safe) ? safe : "";
    }

    private String sanitizeTag(String tag) {
        String safe = normalizeSingleLine(tag, 64);
        if (!StringUtils.hasText(safe)) {
            return DEFAULT_TAG;
        }
        if (safe.length() > 24 || safe.contains("?") || safe.contains("？") || safe.contains("。")) {
            return DEFAULT_TAG;
        }
        return safe;
    }

    private String normalizeSingleLine(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String safe = text
            .replaceAll("\\s+", " ")
            .replaceAll("^[：:;；，,。.\\-\\s]+", "")
            .replaceAll("[：:;；，,。.\\-\\s]+$", "")
            .trim();
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, maxLength).trim();
    }

    private String normalizeMultiline(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace('\u0000', ' ')
            .replaceAll("[\\r\\t]+", " ")
            .replaceAll("[ ]{2,}", " ")
            .replaceAll("\\s*\\n\\s*", "\n")
            .trim();
    }

    private String normalizeBlock(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String[] lines = text.replace('\r', '\n').split("\n");
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            String safe = line == null ? "" : line.trim();
            safe = safe.replaceFirst("^(?:[-*•]+|\\d+[\\.)、])\\s*", "");
            safe = safe.replaceFirst("(?i)^(?:q(?:uestion)?|a(?:nswer)?)\\s*[:：]\\s*", "");
            safe = safe.replaceFirst("^(?:问题|答案|解析|参考答案|标准答案)\\s*[:：]\\s*", "");
            safe = safe.replaceAll("\\s{2,}", " ");
            if (safe.matches("^[|:\\-\\s]+$")) {
                continue;
            }
            if (StringUtils.hasText(safe)) {
                cleaned.add(safe);
            }
        }
        return String.join("\n", cleaned).trim();
    }

    private String firstNotBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return "";
    }

    private record ExtractionResult(String position, List<ExtractedQa> items) {
    }

    private record AiExtractResult(String position, List<ExtractedQa> items) {
    }

    private record ExtractedQa(String tag, String question, String answer) {
    }
}
