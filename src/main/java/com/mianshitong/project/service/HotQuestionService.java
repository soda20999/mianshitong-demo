package com.mianshitong.project.service;

import com.mianshitong.project.entity.po.HotQuestionPo;
import com.mianshitong.project.entity.vo.HotQuestionPracticeScoreVo;
import java.util.List;

public interface HotQuestionService {
    List<HotQuestionPo> list(Long userId, String tag, String position);

    HotQuestionPo detail(Long userId, Long id, boolean recordView);

    HotQuestionPo action(Long userId, Long id, String action);

    HotQuestionPo favorite(Long userId, Long id, boolean favorite);

    HotQuestionPracticeScoreVo scorePractice(Long userId, Long id, String answer);

    void rebuildFromQuestionBank(Long userId, Long bankFileId, String fileName, String content, String defaultPosition);

    void removeByQuestionBank(Long userId, Long bankFileId);

    void removeByQuestionBanks(Long userId, List<Long> bankFileIds);
}
