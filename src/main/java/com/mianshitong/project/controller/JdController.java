package com.mianshitong.project.controller;

import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.entity.dto.JdAnalyzeRequest;
import com.mianshitong.project.entity.po.JdAnalysisPo;
import com.mianshitong.project.service.JdService;
import com.mianshitong.project.util.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jd")
@RequiredArgsConstructor
public class JdController {

    private final JdService jdService;

    @PostMapping("/analyze")
    public ApiResult<JdAnalysisPo> analyze(@Valid @RequestBody JdAnalyzeRequest request) {
        return ApiResult.ok(jdService.analyze(AuthContext.currentUserId(), request));
    }

    @GetMapping("/history")
    public ApiResult<List<JdAnalysisPo>> history() {
        return ApiResult.ok(jdService.listByUser(AuthContext.currentUserId()));
    }
}
