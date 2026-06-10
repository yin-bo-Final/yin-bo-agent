package com.yinbo.agent.admin.controller;

import com.yinbo.agent.admin.AdminGuard;
import com.yinbo.agent.admin.dto.IntentNodeEnabledRequest;
import com.yinbo.agent.admin.dto.IntentNodeRequest;
import com.yinbo.agent.admin.dto.IntentNodeResponse;
import com.yinbo.agent.admin.dto.IntentRuleEnabledRequest;
import com.yinbo.agent.admin.dto.IntentRuleRequest;
import com.yinbo.agent.admin.dto.IntentRuleResponse;
import com.yinbo.agent.admin.service.AdminIntentService;
import com.yinbo.agent.admin.service.AdminIntentRuleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/intents")
// 管理后台意图树配置接口。
public class AdminIntentController {

    private final AdminGuard adminGuard;
    private final AdminIntentService adminIntentService;
    private final AdminIntentRuleService adminIntentRuleService;

    // 注入管理员校验和意图树维护服务。
    public AdminIntentController(
            AdminGuard adminGuard,
            AdminIntentService adminIntentService,
            AdminIntentRuleService adminIntentRuleService
    ) {
        this.adminGuard = adminGuard;
        this.adminIntentService = adminIntentService;
        this.adminIntentRuleService = adminIntentRuleService;
    }

    @GetMapping("/tree")
    // 查询意图树结构。
    public List<IntentNodeResponse> tree(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return adminIntentService.tree();
    }

    @GetMapping("/nodes")
    // 查询扁平意图节点列表。
    public List<IntentNodeResponse> nodes(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return adminIntentService.nodes();
    }

    @PostMapping("/nodes")
    // 创建意图节点。
    public IntentNodeResponse create(
            HttpServletRequest request,
            @Valid @RequestBody IntentNodeRequest nodeRequest
    ) {
        adminGuard.requireAdmin(request);
        return adminIntentService.create(nodeRequest);
    }

    @PatchMapping("/nodes/{nodeId}")
    // 修改意图节点。
    public IntentNodeResponse update(
            HttpServletRequest request,
            @PathVariable Long nodeId,
            @Valid @RequestBody IntentNodeRequest nodeRequest
    ) {
        adminGuard.requireAdmin(request);
        return adminIntentService.update(nodeId, nodeRequest);
    }

    @PatchMapping("/nodes/{nodeId}/enabled")
    // 启用或禁用意图节点。
    public IntentNodeResponse updateEnabled(
            HttpServletRequest request,
            @PathVariable Long nodeId,
            @RequestBody IntentNodeEnabledRequest enabledRequest
    ) {
        adminGuard.requireAdmin(request);
        return adminIntentService.updateEnabled(nodeId, enabledRequest != null && Boolean.TRUE.equals(enabledRequest.enabled()));
    }

    @DeleteMapping("/nodes/{nodeId}")
    // 删除意图节点。
    public void delete(HttpServletRequest request, @PathVariable Long nodeId) {
        adminGuard.requireAdmin(request);
        adminIntentService.delete(nodeId);
    }

    @GetMapping("/rules")
    // 查询意图规则列表。
    public List<IntentRuleResponse> rules(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return adminIntentRuleService.rules();
    }

    @PostMapping("/rules")
    // 创建意图规则。
    public IntentRuleResponse createRule(
            HttpServletRequest request,
            @Valid @RequestBody IntentRuleRequest ruleRequest
    ) {
        adminGuard.requireAdmin(request);
        return adminIntentRuleService.create(ruleRequest);
    }

    @PatchMapping("/rules/{ruleId}")
    // 修改意图规则。
    public IntentRuleResponse updateRule(
            HttpServletRequest request,
            @PathVariable Long ruleId,
            @Valid @RequestBody IntentRuleRequest ruleRequest
    ) {
        adminGuard.requireAdmin(request);
        return adminIntentRuleService.update(ruleId, ruleRequest);
    }

    @PatchMapping("/rules/{ruleId}/enabled")
    // 启用或禁用意图规则。
    public IntentRuleResponse updateRuleEnabled(
            HttpServletRequest request,
            @PathVariable Long ruleId,
            @RequestBody IntentRuleEnabledRequest enabledRequest
    ) {
        adminGuard.requireAdmin(request);
        return adminIntentRuleService.updateEnabled(ruleId, enabledRequest != null && Boolean.TRUE.equals(enabledRequest.enabled()));
    }

    @DeleteMapping("/rules/{ruleId}")
    // 删除意图规则。
    public void deleteRule(HttpServletRequest request, @PathVariable Long ruleId) {
        adminGuard.requireAdmin(request);
        adminIntentRuleService.delete(ruleId);
    }
}
