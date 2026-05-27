package com.mianshitong.project.service.impl;

import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.bo.AiCallResult;
import com.mianshitong.project.entity.bo.AiUsage;
import com.mianshitong.project.entity.bo.ReportAiResult;
import com.mianshitong.project.entity.po.InterviewMessagePo;
import com.mianshitong.project.entity.po.InterviewSessionPo;
import com.mianshitong.project.entity.po.QuestionPo;
import com.mianshitong.project.entity.po.ScoreDetailPo;
import com.mianshitong.project.entity.vo.ResumeParseResultVo;
import com.mianshitong.project.enum_.InterviewStyle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class SpringAiEngine {

    private static final Logger log = LoggerFactory.getLogger(SpringAiEngine.class);

    private static final int MAX_ITEM_LENGTH = 300;
    private static final int MAX_AI_TEXT_LENGTH = 600;
    private static final int MAX_SCORE_SUGGESTION_LENGTH = 220;
    private static final int MAX_SCORE_RECOMMENDED_ANSWER_LENGTH = 700;
    private static final int MAX_FOLLOW_UP_LENGTH = 160;
    private static final int MAX_QUESTION_COUNT = 50;
    private static final int MAX_QUESTION_BANK_ITEMS = 80;
    private static final int MAX_RAG_CONTEXT_LENGTH = 6000;
    private static final int RAG_TOP_K = 8;
    private static final int RAG_CHUNK_SIZE = 420;
    private static final int RAG_CHUNK_OVERLAP = 120;
    private static final int RETRY_SOURCE_LEN_1 = 12000;
    private static final int RETRY_SOURCE_LEN_2 = 8000;
    private static final int RETRY_SOURCE_LEN_3 = 5000;
    private static final String UNKNOWN_ANSWER = "\u9898\u5e93\u539f\u6587\u672a\u63d0\u4f9b\u660e\u786e\u7b54\u6848";
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{IsHan}A-Za-z0-9]+");

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final PromptTemplateSupport promptTemplateSupport;

    public AiCallResult<ResumeParseResultVo> parseResume(String content) {
        String systemPrompt = promptTemplateSupport.resolve(
            "resume-parse",
            "You are a resume parser. Extract facts and avoid fabrication."
        );
        String userPrompt = """
            Resume text:
            %s

            Return JSON only:
            {
              "skills": ["string"],
              "projects": ["string"],
              "education": ["string"],
              "risks": ["string"],
              "deepDivePoints": ["string"],
              "highlights": ["string"]
            }
            Rules:
            1) Keep all fields.
            2) Keep each item concise and verifiable.
            3) Use source content only.
            """.formatted(limit(content, 20000));

        String promptWithRag = withRagContext(
            userPrompt,
            "resume parse skills projects education risks",
            List.of(content),
            "resume-parse"
        );
        AiCallResult<ResumeParseResultVo> result = callForJson(
            "resume parse",
            buildSystemPrompt(systemPrompt),
            promptWithRag,
            ResumeParseResultVo.class
        );
        return new AiCallResult<>(normalizeResume(result.data()), result.usage());
    }

    public AiCallResult<Map<String, List<String>>> analyzeJd(String jobTitle, String jdContent) {
        String systemPrompt = promptTemplateSupport.resolve(
            "jd-analyze",
            "You analyze a job description and output structured interview prep."
        );
        String userPrompt = """
            Job title: %s
            JD content:
            %s

            Return JSON only:
            {
              "keywords": ["string"],
              "coreSkills": ["string"],
              "focuses": ["string"],
              "suggestions": ["string"]
            }
            Rules:
            1) Keep all fields.
            2) Each list should contain concise, actionable items.
            3) Use source content only.
            """.formatted(limit(jobTitle, 120), limit(jdContent, 20000));

        String promptWithRag = withRagContext(
            userPrompt,
            "jd analyze keywords core skills focuses suggestions " + limit(jobTitle, 120),
            List.of(jdContent),
            "jd-analyze"
        );
        AiCallResult<JdAnalyzePayload> result = callForJson(
            "jd analyze",
            buildSystemPrompt(systemPrompt),
            promptWithRag,
            JdAnalyzePayload.class
        );
        JdAnalyzePayload data = result.data() == null ? new JdAnalyzePayload() : result.data();
        Map<String, List<String>> parsed = new LinkedHashMap<>();
        parsed.put("keywords", ensureRange(data.getKeywords(), 3, 10, "Keyword pending "));
        parsed.put("coreSkills", ensureRange(data.getCoreSkills(), 3, 10, "Core skill pending "));
        parsed.put("focuses", ensureRange(data.getFocuses(), 3, 10, "Interview focus pending "));
        parsed.put("suggestions", ensureRange(data.getSuggestions(), 3, 10, "Suggestion pending "));
        return new AiCallResult<>(parsed, result.usage());
    }

    public AiCallResult<QuestionBankExtractPayload> extractQuestionBankQa(
        String fileName,
        String positionHint,
        String content,
        int maxCount
    ) {
        int safeCount = Math.max(1, Math.min(maxCount, MAX_QUESTION_BANK_ITEMS));
        String systemPrompt = promptTemplateSupport.resolve(
            "question-bank-extract",
            "You extract structured QA from source text. Never fabricate."
        );
        List<Integer> sourceAttempts = List.of(RETRY_SOURCE_LEN_1, RETRY_SOURCE_LEN_2, RETRY_SOURCE_LEN_3);
        BizException lastError = null;

        for (int maxSourceLength : sourceAttempts) {
            String scopedContent = limit(content, maxSourceLength);
            if (!StringUtils.hasText(scopedContent)) {
                continue;
            }
            String userPrompt = """
                fileName: %s
                positionHint: %s
                source:
                %s

                Return JSON only:
                {
                  "position": "string",
                  "items": [
                    {
                      "tag": "string",
                      "question": "string",
                      "answer": "string"
                    }
                  ]
                }
                Rules:
                1) Use source text only.
                2) If no explicit answer, set answer to "%s".
                3) Max items: %d.
                4) Keep wording close to source.
                """.formatted(
                limit(fileName, 200),
                limit(positionHint, 120),
                scopedContent,
                UNKNOWN_ANSWER,
                safeCount
            );
            try {
                AiCallResult<QuestionBankExtractPayload> result = callForJson(
                    "question bank extract",
                    buildSystemPrompt(systemPrompt),
                    userPrompt,
                    QuestionBankExtractPayload.class
                );
                return new AiCallResult<>(
                    normalizeQuestionBankExtract(result.data(), positionHint, safeCount),
                    result.usage()
                );
            } catch (BizException ex) {
                lastError = ex;
                log.warn("question-bank-extract failed on source length {}: {}", maxSourceLength, ex.getMessage());
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new BizException("\u9898\u5e93\u63d0\u53d6\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5");
    }

    public AiCallResult<List<QuestionPo>> generateQuestions(
        String jobTitle,
        String direction,
        String level,
        String companyStyle,
        List<String> categories,
        int count,
        String resumeContent,
        ResumeParseResultVo resumeParseResult
    ) {
        String systemPrompt = promptTemplateSupport.resolve(
            "question-generate",
            "You are a senior interviewer. Generate concrete, evaluable interview questions."
        );
        int expectedCount = Math.max(1, Math.min(count, MAX_QUESTION_COUNT));
        String resumeSummary = buildResumeSummary(resumeParseResult, resumeContent);
        String userPrompt = """
            Job title: %s
            Direction: %s
            Level: %s
            Company style: %s
            Categories: %s
            Count: %d
            Candidate summary:
            %s

            Return JSON only:
            {
              "questions": [
                {
                  "category": "string",
                  "content": "string"
                }
              ]
            }
            Rules:
            1) Keep question count equal to Count.
            2) Questions must be specific and follow-up friendly.
            3) Keep each question concise.
            """.formatted(
            limit(jobTitle, 120),
            limit(direction, 32),
            limit(level, 32),
            limit(companyStyle, 64),
            String.join(", ", categories == null ? List.of() : categories),
            expectedCount,
            resumeSummary
        );
        String promptWithRag = withRagContext(
            userPrompt,
            "generate interview questions " + limit(jobTitle, 120),
            List.of(resumeSummary, resumeContent),
            "question-generate"
        );

        AiCallResult<QuestionGeneratePayload> result = callForJson(
            "question generate",
            buildSystemPrompt(systemPrompt),
            promptWithRag,
            QuestionGeneratePayload.class
        );
        List<QuestionPayloadItem> payload = result.data() == null
            ? List.of()
            : Optional.ofNullable(result.data().getQuestions()).orElse(List.of());
        List<QuestionPo> questions = payload.stream()
            .map(item -> toQuestion(item, direction, level, categories))
            .filter(item -> StringUtils.hasText(item.getContent()))
            .limit(expectedCount)
            .collect(Collectors.toCollection(ArrayList::new));

        if (questions.size() < expectedCount) {
            fillQuestionFallback(questions, expectedCount, jobTitle, direction, level, categories);
        }
        if (questions.isEmpty()) {
            throw new BizException("AI question generation failed, please retry");
        }
        return new AiCallResult<>(questions, result.usage());
    }

    public AiCallResult<ScoreDetailPo> scoreAnswer(
        InterviewStyle style,
        String question,
        String answer,
        List<InterviewMessagePo> recentMessages
    ) {
        String systemPrompt = promptTemplateSupport.resolve(
            "answer-score",
            "You are an interviewer scoring assistant. Score with evidence and concise suggestions."
        );
        String userPrompt = """
            Interview style: %s
            Question: %s
            Candidate answer: %s
            Recent context:
            %s

            Return JSON only:
            {
              "correctness": 0,
              "completeness": 0,
              "logic": 0,
              "expression": 0,
              "depth": 0,
              "total": 0,
              "advantages": ["string"],
              "gaps": ["string"],
              "suggestion": "string",
              "recommendedAnswer": "string"
            }
            Rules:
            1) Scores are integers in [0,100].
            2) Keep suggestions actionable.
            3) Do not fabricate facts not present in the answer/context.
            """.formatted(
            style == null ? "FOLLOW_UP" : style.name(),
            limit(question, 600),
            limit(answer, 5000),
            renderRecentMessages(recentMessages)
        );
        String promptWithRag = withRagContext(
            userPrompt,
            "score answer correctness completeness logic expression depth",
            List.of(question, answer, renderRecentMessages(recentMessages)),
            "answer-score"
        );

        AiCallResult<ScoreDetailPo> result = callForJson(
            "answer score",
            buildSystemPrompt(systemPrompt),
            promptWithRag,
            ScoreDetailPo.class
        );
        return new AiCallResult<>(normalizeScore(result.data()), result.usage());
    }

    public AiCallResult<String> generateFollowUp(
        InterviewStyle style,
        String question,
        String answer,
        List<InterviewMessagePo> recentMessages
    ) {
        String systemPrompt = promptTemplateSupport.resolve(
            "interview-follow-up",
            "You are an interviewer. Generate one sharp follow-up question."
        );
        String userPrompt = """
            Interview style: %s
            Current question: %s
            Candidate answer: %s
            Recent context:
            %s

            Return JSON only:
            {
              "followUpQuestion": "string"
            }
            Rules:
            1) Must be one interrogative sentence.
            2) Must directly follow candidate answer details.
            3) Length within 15-80 Chinese chars or equivalent concise English.
            """.formatted(
            style == null ? "FOLLOW_UP" : style.name(),
            limit(question, 600),
            limit(answer, 5000),
            renderRecentMessages(recentMessages)
        );
        String promptWithRag = withRagContext(
            userPrompt,
            "generate follow up question " + limit(question, 120),
            List.of(question, answer, renderRecentMessages(recentMessages)),
            "follow-up"
        );

        AiCallResult<FollowUpPayload> result = callForJson(
            "follow-up",
            buildSystemPrompt(systemPrompt),
            promptWithRag,
            FollowUpPayload.class
        );
        String followUp = result.data() == null ? "" : sanitizeText(result.data().getFollowUpQuestion(), MAX_FOLLOW_UP_LENGTH);
        if (!StringUtils.hasText(followUp)) {
            followUp = "\u8bf7\u7ed3\u5408\u521a\u624d\u7684\u56de\u7b54\uff0c\u8bf4\u660e\u4f60\u7684\u65b9\u6848\u53d6\u820d\u3001\u98ce\u9669\u548c\u9a8c\u8bc1\u65b9\u5f0f\u3002";
        }
        return new AiCallResult<>(followUp, result.usage());
    }

    public AiCallResult<ReportAiResult> generateReport(
        String jobTitle,
        InterviewSessionPo session,
        List<QuestionPo> questions
    ) {
        String systemPrompt = promptTemplateSupport.resolve(
            "report-generate",
            "You are an interview report writer. Output structured, evidence-based feedback."
        );

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("jobTitle", limit(jobTitle, 120));
        context.put("style", session == null || session.getStyle() == null ? "FOLLOW_UP" : session.getStyle().name());
        context.put("sessionTitle", session == null ? "" : limit(session.getTitle(), 120));
        context.put("totalScore", session == null ? null : session.getTotalScore());
        context.put("messages", session == null ? List.of() : Optional.ofNullable(session.getMessages()).orElse(List.of()));
        context.put("scoreHistory", session == null ? List.of() : Optional.ofNullable(session.getScoreHistory()).orElse(List.of()));
        context.put("questionList", questions == null ? List.of() : questions.stream()
            .map(QuestionPo::getContent)
            .filter(StringUtils::hasText)
            .toList());
        String contextJson = toJsonSilently(context);

        String userPrompt = """
            Context:
            %s

            Return JSON only:
            {
              "overallScore": 0,
              "dimensions": {
                "正确性": 0,
                "完整性": 0,
                "条理性": 0,
                "表达": 0,
                "技术深度": 0
              },
              "weakPoints": ["string"],
              "reviewRoadmap": ["string"],
              "questionList": ["string"],
              "userAnswerHighlights": ["string"],
              "aiStandardAnswers": ["string"],
              "brightSpots": ["string"]
            }
            Rules:
            1) Scores are integers in [0,100].
            2) All array fields should be concise and actionable.
            3) Keep output grounded in context.
            """.formatted(contextJson);

        String promptWithRag = withRagContext(
            userPrompt,
            "generate interview report " + limit(jobTitle, 120),
            List.of(contextJson),
            "report-generate"
        );
        AiCallResult<ReportAiResult> result = callForJson(
            "report generate",
            buildSystemPrompt(systemPrompt),
            promptWithRag,
            ReportAiResult.class
        );
        return new AiCallResult<>(normalizeReport(result.data(), session, questions), result.usage());
    }

    private <T> AiCallResult<T> callForJson(String scene, String systemPrompt, String userPrompt, Class<T> clazz) {
        try {
            ChatResponse response = chatModel.call(
                new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
                ))
            );
            String raw = extractOutputText(response);
            if (!StringUtils.hasText(raw)) {
                throw new BizException(scene + " failed: empty AI response");
            }
            T parsed = parseJsonSilently(raw, clazz);
            AiUsage usage = extractUsage(response);
            if (parsed != null) {
                return new AiCallResult<>(parsed, usage);
            }

            ChatResponse retry = chatModel.call(
                new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(buildJsonRepairPrompt(raw))
                ))
            );
            String retryRaw = extractOutputText(retry);
            if (!StringUtils.hasText(retryRaw)) {
                throw new BizException(scene + " failed: empty AI response");
            }
            T repaired = parseJsonSilently(retryRaw, clazz);
            if (repaired == null) {
                throw new BizException(scene + " failed: invalid JSON from AI");
            }
            return new AiCallResult<>(repaired, usage.plus(extractUsage(retry)));
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI scene {} failed: {}", scene, ex.toString());
            throw new BizException(scene + " failed: " + buildAiErrorMessage(ex));
        }
    }

    private String buildAiErrorMessage(Exception ex) {
        String message = ex == null ? "" : ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "AI service unavailable, please retry later";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("error while extracting response for type")
            || lower.contains("openaiapi$chatcompletion")) {
            return "\u0041\u0049\u670d\u52a1\u8fd4\u56de\u683c\u5f0f\u5f02\u5e38\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\uff1b\u5982\u4ecd\u5931\u8d25\u8bf7\u5c06\u9898\u5e93\u62c6\u5206\u540e\u4e0a\u4f20";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "\u0041\u0049\u670d\u52a1\u8bf7\u6c42\u8d85\u65f6\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
        }
        if (lower.contains("429") || lower.contains("rate")) {
            return "\u0041\u0049\u8bf7\u6c42\u8fc7\u4e8e\u9891\u7e41\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5";
        }
        return limit(message, 240);
    }

    private ObjectMapper parser() {
        return objectMapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private <T> T parseJsonSilently(String raw, Class<T> clazz) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return parser().readValue(stripToJson(raw), clazz);
        } catch (Exception ignore) {
            return null;
        }
    }

    private String buildJsonRepairPrompt(String raw) {
        return """
            Your previous response was not valid JSON.
            Return one complete JSON object only, without markdown.
            Previous response:
            %s
            """.formatted(limit(raw, 12000));
    }

    private String buildSystemPrompt(String modulePrompt) {
        return modulePrompt + """

            You must return valid JSON.
            Do not output markdown code blocks.
            Keep outputs concise and verifiable.
            If evidence is insufficient, keep conservative wording.
            """;
    }

    private String withRagContext(String userPrompt, String query, List<String> knowledgeDocs, String scene) {
        String ragContext = buildRagContext(query, knowledgeDocs, RAG_TOP_K);
        if (!StringUtils.hasText(ragContext)) {
            return userPrompt;
        }
        return """
            %s

            ===== Retrieved context =====
            Scene: %s
            Query: %s
            Evidence chunks:
            %s

            Use the evidence above as priority and avoid contradiction.
            """.formatted(
            userPrompt,
            limit(scene, 60),
            limit(query, 240),
            ragContext
        );
    }

    private String buildRagContext(String query, List<String> knowledgeDocs, int topK) {
        if (knowledgeDocs == null || knowledgeDocs.isEmpty()) {
            return "";
        }
        List<String> chunks = new ArrayList<>();
        for (String doc : knowledgeDocs) {
            chunks.addAll(splitToChunks(limit(doc, 50000), RAG_CHUNK_SIZE, RAG_CHUNK_OVERLAP));
        }
        if (chunks.isEmpty()) {
            return "";
        }

        Set<String> queryTokens = tokenizeForRetrieval(query);
        List<ScoredChunk> scored = new ArrayList<>();
        for (String chunk : chunks) {
            String normalized = sanitizeParagraph(chunk, 1200);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            double score = retrievalScore(normalized, queryTokens);
            if (score <= 0) {
                continue;
            }
            scored.add(new ScoredChunk(normalized, score));
        }
        if (scored.isEmpty()) {
            return "";
        }

        List<String> selected = scored.stream()
            .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
            .map(ScoredChunk::chunk)
            .distinct()
            .limit(Math.max(1, topK))
            .toList();

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) {
                builder.append("\n\n");
            }
            builder.append("[").append(i + 1).append("] ").append(selected.get(i));
        }
        return limit(builder.toString(), MAX_RAG_CONTEXT_LENGTH);
    }

    private List<String> splitToChunks(String text, int chunkSize, int overlap) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String safe = text.replace("\r", "\n").trim();
        if (safe.length() <= chunkSize) {
            return List.of(safe);
        }
        List<String> chunks = new ArrayList<>();
        int step = Math.max(50, chunkSize - Math.max(0, overlap));
        for (int start = 0; start < safe.length(); start += step) {
            int end = Math.min(safe.length(), start + chunkSize);
            String chunk = safe.substring(start, end).trim();
            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }
            if (end >= safe.length()) {
                break;
            }
        }
        return chunks;
    }

    private double retrievalScore(String chunk, Set<String> queryTokens) {
        if (!StringUtils.hasText(chunk)) {
            return 0D;
        }
        if (queryTokens == null || queryTokens.isEmpty()) {
            return Math.log(1 + chunk.length());
        }
        Set<String> chunkTokens = tokenizeForRetrieval(chunk);
        int overlap = 0;
        for (String token : queryTokens) {
            if (chunkTokens.contains(token)) {
                overlap++;
            }
        }
        double coverage = (double) overlap / queryTokens.size();
        return coverage * 100 + Math.log(1 + chunk.length());
    }

    private Set<String> tokenizeForRetrieval(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        String[] tokens = TOKEN_SPLITTER.split(text.toLowerCase(Locale.ROOT));
        Set<String> result = new LinkedHashSet<>();
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String safe = token.trim();
            if (safe.length() <= 1) {
                continue;
            }
            result.add(limit(safe, 40));
        }
        return result;
    }

    private QuestionBankExtractPayload normalizeQuestionBankExtract(
        QuestionBankExtractPayload data,
        String positionHint,
        int maxCount
    ) {
        QuestionBankExtractPayload payload = data == null ? new QuestionBankExtractPayload() : data;
        String position = sanitizeText(payload.getPosition(), 120);
        if (!StringUtils.hasText(position)) {
            position = sanitizeText(positionHint, 120);
        }
        if (!StringUtils.hasText(position)) {
            position = "\u901a\u7528\u5c97\u4f4d";
        }

        List<QuestionBankExtractItem> source = payload.getItems() == null ? List.of() : payload.getItems();
        List<QuestionBankExtractItem> sanitized = new ArrayList<>();
        Set<String> deduplicated = new LinkedHashSet<>();
        for (QuestionBankExtractItem item : source) {
            if (item == null) {
                continue;
            }
            String tag = sanitizeText(item.getTag(), 64);
            String question = sanitizeParagraph(item.getQuestion(), 900);
            String answer = sanitizeParagraph(item.getAnswer(), 1800);
            if (!StringUtils.hasText(question)) {
                continue;
            }
            if (!StringUtils.hasText(answer)) {
                answer = UNKNOWN_ANSWER;
            }
            String key = question + "\n---\n" + answer;
            if (!deduplicated.add(key)) {
                continue;
            }
            QuestionBankExtractItem clean = new QuestionBankExtractItem();
            clean.setTag(StringUtils.hasText(tag) ? tag : "\u7efc\u5408");
            clean.setQuestion(question);
            clean.setAnswer(answer);
            sanitized.add(clean);
            if (sanitized.size() >= Math.max(1, maxCount)) {
                break;
            }
        }
        payload.setPosition(position);
        payload.setItems(sanitized);
        return payload;
    }

    private ResumeParseResultVo normalizeResume(ResumeParseResultVo data) {
        ResumeParseResultVo result = data == null ? new ResumeParseResultVo() : data;
        result.setSkills(ensureRange(result.getSkills(), 3, 12, "\u6280\u80fd\u5f85\u8865\u5145"));
        result.setProjects(ensureRange(result.getProjects(), 1, 8, "\u9879\u76ee\u7ecf\u5386\u5f85\u8865\u5145"));
        result.setEducation(ensureRange(result.getEducation(), 1, 6, "\u6559\u80b2\u7ecf\u5386\u5f85\u8865\u5145"));
        result.setRisks(ensureRange(result.getRisks(), 1, 8, "\u98ce\u9669\u70b9\u5f85\u8865\u5145"));
        result.setDeepDivePoints(ensureRange(result.getDeepDivePoints(), 1, 8, "\u6df1\u6316\u70b9\u5f85\u8865\u5145"));
        result.setHighlights(ensureRange(result.getHighlights(), 1, 8, "\u4eae\u70b9\u5f85\u8865\u5145"));
        return result;
    }

    private ScoreDetailPo normalizeScore(ScoreDetailPo data) {
        ScoreDetailPo score = data == null ? new ScoreDetailPo() : data;
        score.setCorrectness(clampScore(score.getCorrectness()));
        score.setCompleteness(clampScore(score.getCompleteness()));
        score.setLogic(clampScore(score.getLogic()));
        score.setExpression(clampScore(score.getExpression()));
        score.setDepth(clampScore(score.getDepth()));
        int total = score.getTotal() == null
            ? avg(score.getCorrectness(), score.getCompleteness(), score.getLogic(), score.getExpression(), score.getDepth())
            : clampScore(score.getTotal());
        score.setTotal(total);
        score.setAdvantages(ensureRange(score.getAdvantages(), 1, 6, "\u4f18\u52bf\u5f85\u8865\u5145"));
        score.setGaps(ensureRange(score.getGaps(), 1, 6, "\u6539\u8fdb\u70b9\u5f85\u8865\u5145"));
        score.setSuggestion(sanitizeParagraph(score.getSuggestion(), MAX_SCORE_SUGGESTION_LENGTH));
        score.setRecommendedAnswer(sanitizeParagraph(score.getRecommendedAnswer(), MAX_SCORE_RECOMMENDED_ANSWER_LENGTH));
        if (!StringUtils.hasText(score.getSuggestion())) {
            score.setSuggestion("\u5efa\u8bae\u8865\u5145\u65b9\u6848\u53d6\u820d\uff0c\u8fb9\u754c\u6761\u4ef6\u4e0e\u9a8c\u8bc1\u65b9\u5f0f\u3002");
        }
        if (!StringUtils.hasText(score.getRecommendedAnswer())) {
            score.setRecommendedAnswer("\u5efa\u8bae\u6309\u201c\u80cc\u666f-\u96be\u70b9-\u65b9\u6848-\u53d6\u820d-\u7ed3\u679c\u201d\u7ed3\u6784\u56de\u7b54\u3002");
        }
        return score;
    }

    private ReportAiResult normalizeReport(ReportAiResult data, InterviewSessionPo session, List<QuestionPo> questions) {
        ReportAiResult report = data == null ? new ReportAiResult() : data;
        if (report.getOverallScore() == null && session != null) {
            report.setOverallScore(clampScore(session.getTotalScore()));
        } else {
            report.setOverallScore(clampScore(report.getOverallScore()));
        }

        Map<String, Integer> dimensions = report.getDimensions() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(report.getDimensions());
        dimensions.putIfAbsent("\u6b63\u786e\u6027", 0);
        dimensions.putIfAbsent("\u5b8c\u6574\u6027", 0);
        dimensions.putIfAbsent("\u6761\u7406\u6027", 0);
        dimensions.putIfAbsent("\u8868\u8fbe", 0);
        dimensions.putIfAbsent("\u6280\u672f\u6df1\u5ea6", 0);
        dimensions.replaceAll((k, v) -> clampScore(v));
        report.setDimensions(dimensions);

        report.setWeakPoints(ensureRange(report.getWeakPoints(), 3, 10, "\u8584\u5f31\u70b9\u5f85\u8865\u5145"));
        report.setReviewRoadmap(ensureRange(report.getReviewRoadmap(), 3, 10, "\u590d\u4e60\u8def\u7ebf\u5f85\u8865\u5145"));
        report.setUserAnswerHighlights(ensureRange(report.getUserAnswerHighlights(), 1, 10, "\u56de\u7b54\u6458\u5f55\u5f85\u8865\u5145"));
        report.setAiStandardAnswers(ensureRange(report.getAiStandardAnswers(), 1, 10, "\u6807\u51c6\u8868\u8fbe\u5f85\u8865\u5145"));
        report.setBrightSpots(ensureRange(report.getBrightSpots(), 1, 10, "\u4eae\u70b9\u5f85\u8865\u5145"));

        List<String> questionList = sanitizeList(report.getQuestionList(), 30);
        if (questionList.isEmpty() && questions != null) {
            questionList = questions.stream()
                .map(QuestionPo::getContent)
                .filter(StringUtils::hasText)
                .limit(30)
                .toList();
        }
        report.setQuestionList(ensureRange(questionList, 1, 30, "\u9762\u8bd5\u95ee\u9898\u5f85\u8865\u5145"));
        return report;
    }

    private int avg(Integer... numbers) {
        int sum = 0;
        int n = 0;
        for (Integer number : numbers) {
            if (number == null) {
                continue;
            }
            sum += clampScore(number);
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    private int clampScore(Integer score) {
        if (score == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, score));
    }

    private void fillQuestionFallback(
        List<QuestionPo> questions,
        int expectedCount,
        String jobTitle,
        String direction,
        String level,
        List<String> categories
    ) {
        List<String> categoryPool = sanitizeCategoryPool(categories);
        Set<String> existingContents = questions.stream()
            .map(QuestionPo::getContent)
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        while (questions.size() < expectedCount) {
            int index = questions.size() + 1;
            QuestionPo question = new QuestionPo();
            question.setCategory(categoryPool.get((index - 1) % categoryPool.size()));
            question.setDirection(limit(direction, 32));
            question.setLevel(limit(level, 32));
            question.setContent(buildFallbackQuestionContent(jobTitle, direction, index));
            while (existingContents.contains(question.getContent())) {
                question.setContent(question.getContent() + " (fallback " + index + ")");
            }
            existingContents.add(question.getContent());
            questions.add(question);
        }
    }

    private List<String> sanitizeCategoryPool(List<String> categories) {
        List<String> safe = categories == null
            ? List.of()
            : categories.stream()
                .map(item -> sanitizeText(item, 30))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (!safe.isEmpty()) {
            return safe;
        }
        return List.of("\u7efc\u5408\u80fd\u529b");
    }

    private String buildFallbackQuestionContent(String jobTitle, String direction, int index) {
        String safeJob = StringUtils.hasText(jobTitle) ? limit(jobTitle, 80) : "\u76ee\u6807\u5c97\u4f4d";
        String safeDirection = StringUtils.hasText(direction) ? limit(direction, 32) : "\u5f53\u524d\u65b9\u5411";
        return sanitizeText(
            "\u95ee\u9898" + index + "\uff1a\u8bf7\u7ed3\u5408\u4f60\u5728" + safeDirection
                + "\u65b9\u5411\u7684\u5b9e\u9645\u9879\u76ee\uff0c\u8bf4\u660e\u4e00\u4e2a\u80fd\u4f53\u73b0\u4f60\u80dc\u4efb"
                + safeJob + "\u5c97\u4f4d\u7684\u6848\u4f8b\uff0c\u5e76\u8bf4\u660e\u65b9\u6848\u53d6\u820d\u4e0e\u6548\u679c\u9a8c\u8bc1\u3002",
            MAX_AI_TEXT_LENGTH
        );
    }

    private QuestionPo toQuestion(
        QuestionPayloadItem item,
        String direction,
        String level,
        List<String> categories
    ) {
        QuestionPo question = new QuestionPo();
        String category = item == null ? "" : sanitizeText(item.getCategory(), 30);
        if (!StringUtils.hasText(category) && categories != null && !categories.isEmpty()) {
            category = categories.get(0);
        }
        question.setCategory(category);
        question.setDirection(limit(direction, 32));
        question.setLevel(limit(level, 32));
        question.setContent(item == null ? "" : sanitizeText(item.getContent(), MAX_AI_TEXT_LENGTH));
        return question;
    }

    private String renderRecentMessages(List<InterviewMessagePo> messages) {
        if (messages == null || messages.isEmpty()) {
            return "none";
        }
        return messages.stream()
            .skip(Math.max(0, messages.size() - 6))
            .map(item -> "[" + limit(item.getRole(), 16) + "] " + limit(item.getContent(), 200))
            .collect(Collectors.joining("\n"));
    }

    private String buildResumeSummary(ResumeParseResultVo parsed, String resumeContent) {
        if (parsed != null) {
            List<String> skills = Optional.ofNullable(parsed.getSkills()).orElse(List.of());
            List<String> projects = Optional.ofNullable(parsed.getProjects()).orElse(List.of());
            String summary = "skills: " + String.join(", ", skills) + "\nprojects: " + String.join(", ", projects);
            if (StringUtils.hasText(summary.replace("skills:", "").replace("projects:", ""))) {
                return limit(summary, 3000);
            }
        }
        return limit(resumeContent, 3000);
    }

    private String extractOutputText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text.trim();
    }

    private AiUsage extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return AiUsage.empty();
        }
        try {
            Object usage = response.getMetadata().getUsage();
            if (usage == null) {
                return AiUsage.empty();
            }
            int promptTokens = readInt(usage, "getPromptTokens");
            int completionTokens = readInt(usage, "getCompletionTokens");
            return new AiUsage(promptTokens, completionTokens);
        } catch (Exception ignore) {
            return AiUsage.empty();
        }
    }

    private int readInt(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            if (value instanceof Number number) {
                return Math.max(0, number.intValue());
            }
        } catch (Exception ignore) {
            // ignore
        }
        return 0;
    }

    private String stripToJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
            text = text.replaceFirst("\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String toJsonSilently(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private List<String> sanitizeList(List<String> source, int limit) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
            .map(item -> sanitizeText(item, MAX_ITEM_LENGTH))
            .filter(StringUtils::hasText)
            .distinct()
            .limit(Math.max(1, limit))
            .toList();
    }

    private List<String> ensureRange(List<String> source, int minCount, int maxCount, String fallbackPrefix) {
        int safeMax = Math.max(1, maxCount);
        int safeMin = Math.max(0, Math.min(minCount, safeMax));
        List<String> sanitized = sanitizeList(source, safeMax);
        if (sanitized.size() >= safeMin) {
            return sanitized;
        }
        List<String> completed = new ArrayList<>(sanitized);
        while (completed.size() < safeMin) {
            int sequence = completed.size() + 1;
            String fallback = sanitizeText(fallbackPrefix + sequence, MAX_ITEM_LENGTH);
            if (!StringUtils.hasText(fallback)) {
                fallback = "Pending " + sequence;
            }
            completed.add(fallback);
        }
        return completed;
    }

    private String sanitizeText(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return limit(text.replaceAll("\\s+", " ").trim(), maxLength);
    }

    private String sanitizeParagraph(String text, int maxLength) {
        String safe = sanitizeText(text, Math.max(maxLength * 3, maxLength));
        if (!StringUtils.hasText(safe)) {
            return "";
        }
        if (safe.length() <= maxLength) {
            return safe;
        }
        int cut = findSentenceBoundary(safe, maxLength);
        return safe.substring(0, Math.max(1, cut)).trim();
    }

    private int findSentenceBoundary(String text, int maxLength) {
        int hardLimit = Math.min(maxLength, text.length());
        int best = -1;
        char[] marks = new char[]{'。', '！', '？', '.', '!', '?', ';', '；'};
        for (char mark : marks) {
            int idx = text.lastIndexOf(mark, hardLimit - 1);
            if (idx > best) {
                best = idx;
            }
        }
        if (best >= maxLength / 2) {
            return best + 1;
        }
        int comma = Math.max(text.lastIndexOf('，', hardLimit - 1), text.lastIndexOf(',', hardLimit - 1));
        if (comma >= maxLength * 2 / 3) {
            return comma + 1;
        }
        return hardLimit;
    }

    private String limit(String text, int maxLength) {
        String safe = text == null ? "" : text.trim();
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength));
    }

    @Data
    public static class QuestionBankExtractPayload {
        private String position;
        private List<QuestionBankExtractItem> items;
    }

    @Data
    public static class QuestionBankExtractItem {
        private String tag;
        private String question;
        private String answer;
    }

    @Data
    private static class JdAnalyzePayload {
        private List<String> keywords;
        private List<String> coreSkills;
        private List<String> focuses;
        private List<String> suggestions;
    }

    @Data
    private static class QuestionGeneratePayload {
        private List<QuestionPayloadItem> questions;
    }

    @Data
    private static class QuestionPayloadItem {
        private String category;
        private String content;
    }

    @Data
    private static class FollowUpPayload {
        private String followUpQuestion;
    }

    private record ScoredChunk(String chunk, double score) {
    }
}

