package com.mianshitong.project.entity.vo;

import com.mianshitong.project.entity.po.ScoreDetailPo;

public record InterviewReplyVo(
    Long sessionId,
    String followUpQuestion,
    ScoreDetailPo scoreDetail
) {
}
