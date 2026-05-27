package com.mianshitong.project.controller;

import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.entity.dto.BatchDeleteQuestionBankRequest;
import com.mianshitong.project.entity.po.QuestionBankFilePo;
import com.mianshitong.project.entity.vo.QuestionBankFileListItemVo;
import com.mianshitong.project.service.QuestionBankService;
import com.mianshitong.project.util.AuthContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/question-banks")
@RequiredArgsConstructor
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<QuestionBankFilePo> upload(@RequestPart("file") MultipartFile file) {
        return ApiResult.ok(questionBankService.upload(AuthContext.currentUserId(), file));
    }

    @GetMapping
    public ApiResult<List<QuestionBankFileListItemVo>> list() {
        List<QuestionBankFileListItemVo> list = questionBankService.listByUser(AuthContext.currentUserId()).stream()
            .map(item -> new QuestionBankFileListItemVo(
                item.getId(),
                item.getFileName(),
                item.getFileType(),
                item.getUploadedAt()
            ))
            .toList();
        return ApiResult.ok(list);
    }

    @GetMapping("/{bankFileId}")
    public ApiResult<QuestionBankFilePo> detail(@PathVariable Long bankFileId) {
        return ApiResult.ok(questionBankService.getById(AuthContext.currentUserId(), bankFileId));
    }

    @DeleteMapping("/{bankFileId}")
    public ApiResult<Void> delete(@PathVariable Long bankFileId) {
        questionBankService.deleteById(AuthContext.currentUserId(), bankFileId);
        return ApiResult.ok("删除成功", null);
    }

    @PostMapping("/batch-delete")
    public ApiResult<Integer> batchDelete(@Valid @RequestBody BatchDeleteQuestionBankRequest request) {
        return ApiResult.ok(questionBankService.batchDelete(AuthContext.currentUserId(), request.bankFileIds()));
    }
}
