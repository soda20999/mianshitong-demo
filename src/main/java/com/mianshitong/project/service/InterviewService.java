package com.mianshitong.project.service;

import com.mianshitong.project.entity.dto.AnswerInterviewRequest;
import com.mianshitong.project.entity.dto.StartInterviewRequest;
import com.mianshitong.project.entity.po.InterviewSessionPo;
import com.mianshitong.project.entity.vo.InterviewReplyVo;
import java.util.List;
import java.util.Map;

public interface InterviewService {
    InterviewSessionPo start(Long userId, StartInterviewRequest request);

    InterviewReplyVo answer(Long userId, Long sessionId, AnswerInterviewRequest request);

    InterviewSessionPo finish(Long userId, Long sessionId);

    List<InterviewSessionPo> listByUser(Long userId);

    Map<String, Object> historyOverview(Long userId);
}
