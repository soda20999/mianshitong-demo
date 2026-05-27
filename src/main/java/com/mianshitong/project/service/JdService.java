package com.mianshitong.project.service;

import com.mianshitong.project.entity.dto.JdAnalyzeRequest;
import com.mianshitong.project.entity.po.JdAnalysisPo;
import java.util.List;

public interface JdService {
    JdAnalysisPo analyze(Long userId, JdAnalyzeRequest request);

    List<JdAnalysisPo> listByUser(Long userId);
}
