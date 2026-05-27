package com.mianshitong.project.controller;

import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.entity.dto.HotQuestionActionRequest;
import com.mianshitong.project.entity.dto.HotQuestionFavoriteRequest;
import com.mianshitong.project.entity.dto.HotQuestionPracticeScoreRequest;
import com.mianshitong.project.entity.po.HotQuestionPo;
import com.mianshitong.project.entity.vo.HotQuestionPracticeScoreVo;
import com.mianshitong.project.service.HotQuestionService;
import com.mianshitong.project.util.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hot-questions")
@RequiredArgsConstructor
public class HotQuestionController {

    private final HotQuestionService hotQuestionService;

    @GetMapping
    public ApiResult<List<HotQuestionPo>> list(
        @RequestParam(required = false) String tag,
        @RequestParam(required = false) String position
    ) {
        return ApiResult.ok(hotQuestionService.list(AuthContext.currentUserId(), tag, position));
    }

    @GetMapping("/{id}")
    public ApiResult<HotQuestionPo> detail(
        @PathVariable Long id,
        @RequestParam(defaultValue = "false") boolean recordView
    ) {
        return ApiResult.ok(hotQuestionService.detail(AuthContext.currentUserId(), id, recordView));
    }

    @PostMapping("/{id}/action")
    public ApiResult<HotQuestionPo> action(@PathVariable Long id, @Valid @RequestBody HotQuestionActionRequest request) {
        return ApiResult.ok(hotQuestionService.action(AuthContext.currentUserId(), id, request.action()));
    }

    @PostMapping("/{id}/favorite")
    public ApiResult<HotQuestionPo> favorite(@PathVariable Long id, @Valid @RequestBody HotQuestionFavoriteRequest request) {
        return ApiResult.ok(hotQuestionService.favorite(AuthContext.currentUserId(), id, request.favorite()));
    }

    @PostMapping("/{id}/practice-score")
    public ApiResult<HotQuestionPracticeScoreVo> practiceScore(
        @PathVariable Long id,
        @Valid @RequestBody HotQuestionPracticeScoreRequest request
    ) {
        return ApiResult.ok(hotQuestionService.scorePractice(AuthContext.currentUserId(), id, request.answer()));
    }
}
