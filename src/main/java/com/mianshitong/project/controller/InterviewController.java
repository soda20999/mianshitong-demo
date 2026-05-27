package com.mianshitong.project.controller;

import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.entity.dto.AnswerInterviewRequest;
import com.mianshitong.project.entity.dto.StartInterviewRequest;
import com.mianshitong.project.entity.po.InterviewSessionPo;
import com.mianshitong.project.entity.vo.InterviewReplyVo;
import com.mianshitong.project.service.InterviewService;
import com.mianshitong.project.util.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/start")
    public ApiResult<InterviewSessionPo> start(@Valid @RequestBody StartInterviewRequest request) {
        return ApiResult.ok(interviewService.start(AuthContext.currentUserId(), request));
    }

    @PostMapping("/{sessionId}/answer")
    public ApiResult<InterviewReplyVo> answer(@PathVariable Long sessionId, @Valid @RequestBody AnswerInterviewRequest request) {
        return ApiResult.ok(interviewService.answer(AuthContext.currentUserId(), sessionId, request));
    }

    @PostMapping("/{sessionId}/finish")
    public ApiResult<InterviewSessionPo> finish(@PathVariable Long sessionId) {
        return ApiResult.ok(interviewService.finish(AuthContext.currentUserId(), sessionId));
    }

    @GetMapping
    public ApiResult<List<InterviewSessionPo>> list() {
        return ApiResult.ok(interviewService.listByUser(AuthContext.currentUserId()));
    }

    @GetMapping("/history-overview")
    public ApiResult<Map<String, Object>> historyOverview() {
        return ApiResult.ok(interviewService.historyOverview(AuthContext.currentUserId()));
    }
}
