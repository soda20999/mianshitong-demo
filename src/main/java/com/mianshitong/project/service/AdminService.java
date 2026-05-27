package com.mianshitong.project.service;

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
import com.mianshitong.project.entity.vo.UserVo;
import java.util.List;
import java.util.Map;

public interface AdminService {
    List<UserVo> users();

    UserVo updateUser(Long userId, AdminUpdateUserRequest request);

    List<ResumePo> resumes();

    List<InterviewSessionPo> interviews();

    List<ReportPo> reports();

    List<PromptTemplatePo> prompts();

    PromptTemplatePo savePrompt(Long promptId, AdminUpdatePromptRequest request);

    List<AiLogPo> aiLogs();

    RiskConfigPo riskConfig();

    RiskConfigPo updateRiskConfig(AdminUpdateRiskConfigRequest request);

    List<SensitiveWordPo> sensitiveWords();

    SensitiveWordPo addSensitiveWord(AdminAddSensitiveWordRequest request);

    Map<String, Object> removeSensitiveWord(Long id);
}
