package com.mianshitong.project.service.impl;

import com.mianshitong.project.entity.bo.AiUsage;
import com.mianshitong.project.entity.po.AiLogPo;
import com.mianshitong.project.mapper.AiLogMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiLogSupport {

    private final AiLogMapper aiLogMapper;
    @Value("${app.ai.prompt-price-per-1k:0}")
    private BigDecimal promptPricePer1k;
    @Value("${app.ai.completion-price-per-1k:0}")
    private BigDecimal completionPricePer1k;

    public void log(Long userId, String module, AiUsage usage, String status) {
        AiUsage safeUsage = usage == null ? AiUsage.empty() : usage;
        log(userId, module, safeUsage.promptTokens(), safeUsage.completionTokens(), status);
    }

    public void log(Long userId, String module, int promptTokens, int completionTokens, String status) {
        int safePromptTokens = Math.max(promptTokens, 0);
        int safeCompletionTokens = Math.max(completionTokens, 0);
        AiLogPo log = new AiLogPo();
        log.setUserId(userId);
        log.setModule(module);
        log.setPromptTokens(safePromptTokens);
        log.setCompletionTokens(safeCompletionTokens);
        log.setCost(calculateCost(safePromptTokens, safeCompletionTokens));
        log.setStatus(status);
        log.setTime(LocalDateTime.now());
        aiLogMapper.insert(log);
    }

    private BigDecimal calculateCost(int promptTokens, int completionTokens) {
        BigDecimal promptCost = promptPricePer1k
            .multiply(BigDecimal.valueOf(promptTokens))
            .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        BigDecimal completionCost = completionPricePer1k
            .multiply(BigDecimal.valueOf(completionTokens))
            .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        return promptCost.add(completionCost).setScale(4, RoundingMode.HALF_UP);
    }
}
