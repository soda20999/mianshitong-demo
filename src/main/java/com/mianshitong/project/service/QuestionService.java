package com.mianshitong.project.service;

import com.mianshitong.project.entity.dto.QuestionGenerateRequest;
import com.mianshitong.project.entity.po.QuestionSetPo;
import java.util.List;

public interface QuestionService {
    QuestionSetPo generate(Long userId, QuestionGenerateRequest request);

    List<QuestionSetPo> listByUser(Long userId);

    QuestionSetPo getOwnedQuestionSet(Long userId, Long questionSetId);
}
