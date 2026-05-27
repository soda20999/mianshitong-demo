package com.mianshitong.project.entity.vo;

import java.util.List;

public record DashboardOverviewVo(
    long resumeCount,
    long questionSetCount,
    long interviewCount,
    long reportCount,
    List<Integer> growthScores
) {
}
