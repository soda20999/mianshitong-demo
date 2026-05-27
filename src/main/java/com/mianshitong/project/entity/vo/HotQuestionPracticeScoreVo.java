package com.mianshitong.project.entity.vo;

import com.mianshitong.project.entity.po.HotQuestionPo;
import com.mianshitong.project.entity.po.ScoreDetailPo;

public record HotQuestionPracticeScoreVo(
    HotQuestionPo question,
    ScoreDetailPo scoreDetail,
    String correctAnswer
) {
}
