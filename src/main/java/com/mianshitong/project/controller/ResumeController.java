package com.mianshitong.project.controller;

import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.entity.po.ResumePo;
import com.mianshitong.project.service.ResumeService;
import com.mianshitong.project.util.AuthContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<ResumePo> upload(@RequestPart("file") MultipartFile file) {
        return ApiResult.ok(resumeService.upload(AuthContext.currentUserId(), file));
    }

    @GetMapping
    public ApiResult<List<ResumePo>> list() {
        return ApiResult.ok(resumeService.listByUser(AuthContext.currentUserId()));
    }

    @PostMapping("/{resumeId}/parse")
    public ApiResult<ResumePo> parse(@PathVariable Long resumeId) {
        return ApiResult.ok(resumeService.parse(AuthContext.currentUserId(), resumeId));
    }
}
