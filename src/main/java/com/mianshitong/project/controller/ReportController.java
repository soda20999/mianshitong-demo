package com.mianshitong.project.controller;

import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.entity.dto.BatchExportReportRequest;
import com.mianshitong.project.entity.dto.FavoriteReportQuestionRequest;
import com.mianshitong.project.entity.po.QuestionSetPo;
import com.mianshitong.project.entity.po.ReportPo;
import com.mianshitong.project.service.ReportService;
import com.mianshitong.project.util.AuthContext;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ApiResult<List<ReportPo>> list() {
        return ApiResult.ok(reportService.listByUser(AuthContext.currentUserId()));
    }

    @GetMapping("/{reportId}")
    public ApiResult<ReportPo> detail(@PathVariable Long reportId) {
        return ApiResult.ok(reportService.getByUser(AuthContext.currentUserId(), reportId));
    }

    @PostMapping("/{reportId}/favorite")
    public ApiResult<Map<String, Boolean>> favoriteQuestion(
        @PathVariable Long reportId,
        @Valid @RequestBody FavoriteReportQuestionRequest request
    ) {
        boolean favorited = reportService.favoriteQuestion(AuthContext.currentUserId(), reportId, request.questionIndex());
        return ApiResult.ok(Map.of("favorited", favorited));
    }

    @PostMapping("/{reportId}/redo-wrong")
    public ApiResult<QuestionSetPo> redoWrong(@PathVariable Long reportId) {
        return ApiResult.ok(reportService.redoWrongQuestions(AuthContext.currentUserId(), reportId));
    }

    @GetMapping("/{reportId}/export-pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long reportId) {
        byte[] bytes = reportService.exportPdf(AuthContext.currentUserId(), reportId);
        String fileName = "report-" + reportId + ".pdf";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentHeader(fileName))
            .body(bytes);
    }

    @PostMapping("/export-pdf")
    public ResponseEntity<byte[]> batchExportPdf(@Valid @RequestBody BatchExportReportRequest request) {
        byte[] bytes = reportService.batchExportPdf(AuthContext.currentUserId(), request.reportIds());
        String suffix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String fileName = "reports-" + suffix + ".zip";
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, buildAttachmentHeader(fileName))
            .body(bytes);
    }

    private String buildAttachmentHeader(String fileName) {
        return ContentDisposition.attachment()
            .filename(fileName, StandardCharsets.UTF_8)
            .build()
            .toString();
    }
}
