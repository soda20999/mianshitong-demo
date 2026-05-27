package com.mianshitong.project.controller.admin;

import com.mianshitong.project.common.result.ApiResult;
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
import com.mianshitong.project.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ApiResult<List<UserVo>> users() {
        return ApiResult.ok(adminService.users());
    }

    @PutMapping("/users/{userId}")
    public ApiResult<UserVo> updateUser(@PathVariable Long userId, @Valid @RequestBody AdminUpdateUserRequest request) {
        return ApiResult.ok(adminService.updateUser(userId, request));
    }

    @GetMapping("/resumes")
    public ApiResult<List<ResumePo>> resumes() {
        return ApiResult.ok(adminService.resumes());
    }

    @GetMapping("/interviews")
    public ApiResult<List<InterviewSessionPo>> interviews() {
        return ApiResult.ok(adminService.interviews());
    }

    @GetMapping("/reports")
    public ApiResult<List<ReportPo>> reports() {
        return ApiResult.ok(adminService.reports());
    }

    @GetMapping("/prompts")
    public ApiResult<List<PromptTemplatePo>> prompts() {
        return ApiResult.ok(adminService.prompts());
    }

    @PostMapping("/prompts")
    public ApiResult<PromptTemplatePo> createPrompt(@Valid @RequestBody AdminUpdatePromptRequest request) {
        return ApiResult.ok(adminService.savePrompt(0L, request));
    }

    @PutMapping("/prompts/{promptId}")
    public ApiResult<PromptTemplatePo> updatePrompt(
        @PathVariable Long promptId,
        @Valid @RequestBody AdminUpdatePromptRequest request
    ) {
        return ApiResult.ok(adminService.savePrompt(promptId, request));
    }

    @GetMapping("/ai-logs")
    public ApiResult<List<AiLogPo>> aiLogs() {
        return ApiResult.ok(adminService.aiLogs());
    }

    @GetMapping("/risk-config")
    public ApiResult<RiskConfigPo> riskConfig() {
        return ApiResult.ok(adminService.riskConfig());
    }

    @PutMapping("/risk-config")
    public ApiResult<RiskConfigPo> updateRiskConfig(@Valid @RequestBody AdminUpdateRiskConfigRequest request) {
        return ApiResult.ok(adminService.updateRiskConfig(request));
    }

    @GetMapping("/sensitive-words")
    public ApiResult<List<SensitiveWordPo>> sensitiveWords() {
        return ApiResult.ok(adminService.sensitiveWords());
    }

    @PostMapping("/sensitive-words")
    public ApiResult<SensitiveWordPo> addSensitiveWord(@Valid @RequestBody AdminAddSensitiveWordRequest request) {
        return ApiResult.ok(adminService.addSensitiveWord(request));
    }

    @DeleteMapping("/sensitive-words/{id}")
    public ApiResult<Map<String, Object>> removeSensitiveWord(@PathVariable Long id) {
        return ApiResult.ok(adminService.removeSensitiveWord(id));
    }
}
