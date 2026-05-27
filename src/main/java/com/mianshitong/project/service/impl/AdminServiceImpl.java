package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.common.exception.BizException;
import com.mianshitong.project.entity.dto.AdminAddSensitiveWordRequest;
import com.mianshitong.project.entity.dto.AdminUpdatePromptRequest;
import com.mianshitong.project.entity.dto.AdminUpdateRiskConfigRequest;
import com.mianshitong.project.entity.dto.AdminUpdateUserRequest;
import com.mianshitong.project.entity.po.AiLogPo;
import com.mianshitong.project.entity.po.InterviewSessionPo;
import com.mianshitong.project.entity.po.PromptTemplatePo;
import com.mianshitong.project.entity.po.ReportPo;
import com.mianshitong.project.entity.po.ResumePo;
import com.mianshitong.project.entity.po.RiskConfigPo;
import com.mianshitong.project.entity.po.SensitiveWordPo;
import com.mianshitong.project.entity.po.UserPo;
import com.mianshitong.project.entity.vo.UserVo;
import com.mianshitong.project.enum_.UserRole;
import com.mianshitong.project.mapper.AiLogMapper;
import com.mianshitong.project.mapper.InterviewSessionMapper;
import com.mianshitong.project.mapper.PromptTemplateMapper;
import com.mianshitong.project.mapper.ResumeMapper;
import com.mianshitong.project.mapper.SensitiveWordMapper;
import com.mianshitong.project.mapper.UserMapper;
import com.mianshitong.project.service.AdminService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final AiLogMapper aiLogMapper;
    private final SensitiveWordMapper sensitiveWordMapper;
    private final RiskConfigSupport riskConfigSupport;
    private final ReportServiceImpl reportService;

    @Override
    public List<UserVo> users() {
        return userMapper.selectList(
            new LambdaQueryWrapper<UserPo>().orderByDesc(UserPo::getCreatedAt)
        ).stream().map(this::toVo).toList();
    }

    @Override
    public UserVo updateUser(Long userId, AdminUpdateUserRequest request) {
        UserPo user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (user.getRole() == UserRole.ADMIN && !Boolean.TRUE.equals(request.enabled())) {
            throw new BizException("管理员账号不可禁用");
        }
        user.setEnabled(request.enabled());
        user.setTargetPosition(request.targetPosition());
        userMapper.updateById(user);
        return toVo(user);
    }

    @Override
    public List<ResumePo> resumes() {
        return resumeMapper.selectList(
            new LambdaQueryWrapper<ResumePo>().orderByDesc(ResumePo::getUploadedAt)
        );
    }

    @Override
    public List<InterviewSessionPo> interviews() {
        return interviewSessionMapper.selectList(
            new LambdaQueryWrapper<InterviewSessionPo>().orderByDesc(InterviewSessionPo::getCreatedAt)
        );
    }

    @Override
    public List<ReportPo> reports() {
        return reportService.allReports();
    }

    @Override
    public List<PromptTemplatePo> prompts() {
        return promptTemplateMapper.selectList(
            new LambdaQueryWrapper<PromptTemplatePo>().orderByDesc(PromptTemplatePo::getUpdatedAt)
        );
    }

    @Override
    public PromptTemplatePo savePrompt(Long promptId, AdminUpdatePromptRequest request) {
        PromptTemplatePo prompt = null;
        if (promptId != null && promptId > 0) {
            prompt = promptTemplateMapper.selectById(promptId);
        }
        boolean create = prompt == null;
        if (create) {
            prompt = new PromptTemplatePo();
        }
        prompt.setModule(request.module());
        prompt.setName(request.name());
        prompt.setContent(request.content());
        prompt.setUpdatedAt(LocalDateTime.now());
        if (create) {
            promptTemplateMapper.insert(prompt);
        } else {
            promptTemplateMapper.updateById(prompt);
        }
        return prompt;
    }

    @Override
    public List<AiLogPo> aiLogs() {
        return aiLogMapper.selectList(
            new LambdaQueryWrapper<AiLogPo>().orderByDesc(AiLogPo::getTime)
        );
    }

    @Override
    public RiskConfigPo riskConfig() {
        return riskConfigSupport.current();
    }

    @Override
    public RiskConfigPo updateRiskConfig(AdminUpdateRiskConfigRequest request) {
        RiskConfigPo config = riskConfigSupport.current();
        config.setRateLimitPerMinute(request.rateLimitPerMinute());
        config.setMaxQuestionGeneratePerDay(request.maxQuestionGeneratePerDay());
        config.setMaxReportGeneratePerDay(request.maxReportGeneratePerDay());
        config.setUploadMaxMb(request.uploadMaxMb());
        config.setUploadAllowTypes(request.uploadAllowTypes());
        config.setInputMaxLength(request.inputMaxLength());
        config.setPromptInjectionCheck(request.promptInjectionCheck());
        config.setOutputSafetyCheck(request.outputSafetyCheck());
        config.setIdempotencyCheck(request.idempotencyCheck());
        return riskConfigSupport.save(config);
    }

    @Override
    public List<SensitiveWordPo> sensitiveWords() {
        return sensitiveWordMapper.selectList(
            new LambdaQueryWrapper<SensitiveWordPo>().orderByDesc(SensitiveWordPo::getCreatedAt)
        );
    }

    @Override
    public SensitiveWordPo addSensitiveWord(AdminAddSensitiveWordRequest request) {
        Long count = sensitiveWordMapper.selectCount(
            new LambdaQueryWrapper<SensitiveWordPo>().eq(SensitiveWordPo::getWord, request.word())
        );
        if (count != null && count > 0) {
            throw new BizException("敏感词已存在");
        }
        SensitiveWordPo word = new SensitiveWordPo();
        word.setWord(request.word());
        word.setEnabled(true);
        word.setCreatedAt(LocalDateTime.now());
        sensitiveWordMapper.insert(word);
        return word;
    }

    @Override
    public Map<String, Object> removeSensitiveWord(Long id) {
        boolean removed = sensitiveWordMapper.deleteById(id) > 0;
        return Map.of("removed", removed);
    }

    private UserVo toVo(UserPo user) {
        return new UserVo(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getAvatar(),
            user.getTargetPosition(),
            user.getPoints(),
            user.getEnabled(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}
