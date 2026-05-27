package com.mianshitong.project.controller;

import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.entity.dto.QuestionGenerateRequest;
import com.mianshitong.project.entity.po.QuestionSetPo;
import com.mianshitong.project.service.QuestionService;
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
@RequestMapping("/api/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping("/generate")
    public ApiResult<QuestionSetPo> generate(@Valid @RequestBody QuestionGenerateRequest request) {
        return ApiResult.ok(questionService.generate(AuthContext.currentUserId(), request));
    }

    @GetMapping
    public ApiResult<List<QuestionSetPo>> list() {
        return ApiResult.ok(questionService.listByUser(AuthContext.currentUserId()));
    }
}
