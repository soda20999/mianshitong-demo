package com.mianshitong.project.controller;

import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.service.InterviewService;
import com.mianshitong.project.util.AuthContext;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final InterviewService interviewService;

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        return ApiResult.ok(interviewService.historyOverview(AuthContext.currentUserId()));
    }
}
