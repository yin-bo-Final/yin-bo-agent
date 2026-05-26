package com.yinbo.agent.admin;

import com.yinbo.agent.admin.dto.AdminDashboardResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminGuard adminGuard;
    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminGuard adminGuard, AdminDashboardService dashboardService) {
        this.adminGuard = adminGuard;
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public AdminDashboardResponse dashboard(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return dashboardService.dashboard();
    }
}
