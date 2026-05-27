package com.mianshitong.project.service.impl;

import com.mianshitong.project.entity.po.RiskConfigPo;
import com.mianshitong.project.mapper.RiskConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RiskConfigSupport {

    private static final long DEFAULT_ID = 1L;

    private final RiskConfigMapper riskConfigMapper;

    public RiskConfigPo current() {
        RiskConfigPo config = riskConfigMapper.selectById(DEFAULT_ID);
        if (config != null) {
            return config;
        }
        RiskConfigPo created = defaultConfig();
        riskConfigMapper.insert(created);
        return created;
    }

    public RiskConfigPo save(RiskConfigPo config) {
        if (config.getId() == null) {
            config.setId(DEFAULT_ID);
        }
        if (riskConfigMapper.selectById(config.getId()) == null) {
            riskConfigMapper.insert(config);
        } else {
            riskConfigMapper.updateById(config);
        }
        return config;
    }

    private RiskConfigPo defaultConfig() {
        RiskConfigPo config = new RiskConfigPo();
        config.setId(DEFAULT_ID);
        config.setRateLimitPerMinute(10);
        config.setMaxQuestionGeneratePerDay(50);
        config.setMaxReportGeneratePerDay(5);
        config.setUploadMaxMb(10);
        config.setUploadAllowTypes("pdf,doc,docx");
        config.setInputMaxLength(20000);
        config.setPromptInjectionCheck(true);
        config.setOutputSafetyCheck(true);
        config.setIdempotencyCheck(true);
        return config;
    }
}
