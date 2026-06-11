package com.yinbo.agent.ingestion.controller;

import com.yinbo.agent.admin.AdminGuard;
import com.yinbo.agent.admin.dto.PageResponse;
import com.yinbo.agent.ingestion.dto.IngestionTaskResponse;
import com.yinbo.agent.ingestion.service.IngestionTaskAdminService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ingestion/tasks")
// 管理后台入库任务接口。
public class IngestionTaskAdminController {

    private final AdminGuard adminGuard;
    private final IngestionTaskAdminService ingestionTaskAdminService;

    // 注入管理员校验和入库任务服务。
    public IngestionTaskAdminController(AdminGuard adminGuard, IngestionTaskAdminService ingestionTaskAdminService) {
        this.adminGuard = adminGuard;
        this.ingestionTaskAdminService = ingestionTaskAdminService;
    }

    @GetMapping("/failed")
    // 分页查询失败入库任务列表。
    public PageResponse<IngestionTaskResponse> failedTasks(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt
    ) {
        adminGuard.requireAdmin(request);
        return ingestionTaskAdminService.pageFailedTasks(page, pageSize, keyword, status, startAt, endAt);
    }

    @PostMapping("/{taskId}/retry")
    // 手动重试失败入库任务。
    public IngestionTaskResponse retryTask(@PathVariable String taskId, HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return ingestionTaskAdminService.retryTask(taskId);
    }

    @DeleteMapping("/{taskId}")
    // 删除失败入库任务。
    public void deleteTask(@PathVariable String taskId, HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        ingestionTaskAdminService.deleteTask(taskId);
    }
}
