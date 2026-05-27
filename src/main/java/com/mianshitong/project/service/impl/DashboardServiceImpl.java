package com.mianshitong.project.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mianshitong.project.entity.po.InterviewSessionPo;
import com.mianshitong.project.entity.po.QuestionSetPo;
import com.mianshitong.project.entity.po.ReportPo;
import com.mianshitong.project.entity.po.ResumePo;
import com.mianshitong.project.entity.vo.DashboardOverviewVo;
import com.mianshitong.project.mapper.InterviewSessionMapper;
import com.mianshitong.project.mapper.QuestionSetMapper;
import com.mianshitong.project.mapper.ReportMapper;
import com.mianshitong.project.mapper.ResumeMapper;
import com.mianshitong.project.service.DashboardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ResumeMapper resumeMapper;
    private final QuestionSetMapper questionSetMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final ReportMapper reportMapper;

    @Override
    public DashboardOverviewVo overview(Long userId) {
        long resumeCount = safeCount(resumeMapper.selectCount(new LambdaQueryWrapper<ResumePo>().eq(ResumePo::getUserId, userId)));
        long questionSetCount = safeCount(questionSetMapper.selectCount(new LambdaQueryWrapper<QuestionSetPo>().eq(QuestionSetPo::getUserId, userId)));
        long interviewCount = safeCount(interviewSessionMapper.selectCount(new LambdaQueryWrapper<InterviewSessionPo>().eq(InterviewSessionPo::getUserId, userId)));
        long reportCount = safeCount(reportMapper.selectCount(new LambdaQueryWrapper<ReportPo>().eq(ReportPo::getUserId, userId)));
        List<Integer> growth = interviewSessionMapper.selectList(
            new LambdaQueryWrapper<InterviewSessionPo>()
                .eq(InterviewSessionPo::getUserId, userId)
                .isNotNull(InterviewSessionPo::getTotalScore)
                .orderByDesc(InterviewSessionPo::getCreatedAt)
                .last("LIMIT 10")
        ).stream().map(InterviewSessionPo::getTotalScore).toList();
        return new DashboardOverviewVo(resumeCount, questionSetCount, interviewCount, reportCount, growth);
    }

    private long safeCount(Long count) {
        return count == null ? 0L : count;
    }
}
