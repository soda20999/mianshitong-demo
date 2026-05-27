package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.entity.bo.AiCallResult;
import com.mianshitong.project.entity.dto.JdAnalyzeRequest;
import com.mianshitong.project.entity.po.JdAnalysisPo;
import com.mianshitong.project.mapper.JdAnalysisMapper;
import com.mianshitong.project.service.JdService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JdServiceImpl implements JdService {

    private final JdAnalysisMapper jdAnalysisMapper;
    private final SpringAiEngine springAiEngine;
    private final AiLogSupport aiLogSupport;
    private final AiRateLimitSupport aiRateLimitSupport;

    @Override
    public JdAnalysisPo analyze(Long userId, JdAnalyzeRequest request) {
        aiRateLimitSupport.checkPerMinuteLimit(userId);
        AiCallResult<Map<String, List<String>>> aiResult = springAiEngine.analyzeJd(request.jobTitle(), request.jdContent());
        Map<String, List<String>> parsed = aiResult.data();
        JdAnalysisPo jd = new JdAnalysisPo();
        jd.setUserId(userId);
        jd.setJobTitle(request.jobTitle());
        jd.setJdContent(request.jdContent());
        jd.setKeywords(parsed.get("keywords"));
        jd.setCoreSkills(parsed.get("coreSkills"));
        jd.setInterviewFocuses(parsed.get("focuses"));
        jd.setSuggestions(parsed.get("suggestions"));
        jd.setCreatedAt(LocalDateTime.now());
        jdAnalysisMapper.insert(jd);

        aiLogSupport.log(userId, "jd", aiResult.usage(), "SUCCESS");
        return jd;
    }

    @Override
    public List<JdAnalysisPo> listByUser(Long userId) {
        return jdAnalysisMapper.selectList(
            new LambdaQueryWrapper<JdAnalysisPo>()
                .eq(JdAnalysisPo::getUserId, userId)
                .orderByDesc(JdAnalysisPo::getCreatedAt)
        );
    }
}
