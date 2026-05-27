package com.mianshitong.project.service;

import com.mianshitong.project.entity.po.QuestionSetPo;
import com.mianshitong.project.entity.po.ReportPo;
import java.util.List;

public interface ReportService {
    List<ReportPo> listByUser(Long userId);

    ReportPo getByUser(Long userId, Long reportId);

    boolean favoriteQuestion(Long userId, Long reportId, Integer questionIndex);

    QuestionSetPo redoWrongQuestions(Long userId, Long reportId);

    byte[] exportPdf(Long userId, Long reportId);

    byte[] batchExportPdf(Long userId, List<Long> reportIds);
}
