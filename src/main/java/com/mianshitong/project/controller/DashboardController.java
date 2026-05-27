package com.mianshitong.project.controller;

import com.mianshitong.project.common.result.ApiResult;
import com.mianshitong.project.entity.vo.DashboardOverviewVo;
import com.mianshitong.project.service.DashboardService;
import com.mianshitong.project.util.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public ApiResult<DashboardOverviewVo> overview() {
        return ApiResult.ok(dashboardService.overview(AuthContext.currentUserId()));
    }
}
