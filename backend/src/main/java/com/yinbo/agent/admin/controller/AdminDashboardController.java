package com.yinbo.agent.admin.controller;

import com.yinbo.agent.admin.AdminGuard;
import com.yinbo.agent.admin.dto.AdminDashboardResponse;
import com.yinbo.agent.admin.service.AdminDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
// 管理后台仪表盘接口。
public class AdminDashboardController {

    private final AdminGuard adminGuard;
    private final AdminDashboardService dashboardService;

    // 注入管理员校验和仪表盘查询服务。
    public AdminDashboardController(AdminGuard adminGuard, AdminDashboardService dashboardService) {
        this.adminGuard = adminGuard;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    // 查询管理后台仪表盘统计数据。
    public AdminDashboardResponse dashboard(
            HttpServletRequest request,
            @RequestParam(defaultValue = "day") String messageRange
    ) {
        adminGuard.requireAdmin(request);
        return dashboardService.dashboard(messageRange);
    }
}
