package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.entity.po.PromptTemplatePo;
import com.mianshitong.project.mapper.PromptTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PromptTemplateSupport {

    private final PromptTemplateMapper promptTemplateMapper;

    public String resolve(String module, String fallbackPrompt) {
        PromptTemplatePo prompt = promptTemplateMapper.selectOne(
            new LambdaQueryWrapper<PromptTemplatePo>()
                .eq(PromptTemplatePo::getModule, module)
                .orderByDesc(PromptTemplatePo::getUpdatedAt)
                .last("LIMIT 1")
        );
        if (prompt == null || !StringUtils.hasText(prompt.getContent())) {
            return fallbackPrompt;
        }
        return prompt.getContent().trim();
    }
}
