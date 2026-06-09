package com.yinbo.agent.admin.controller;

import com.yinbo.agent.admin.AdminGuard;
import com.yinbo.agent.admin.dto.TerminologyMappingRequest;
import com.yinbo.agent.admin.dto.TerminologyMappingResponse;
import com.yinbo.agent.admin.dto.UpdateQueryPipelineConfigRequest;
import com.yinbo.agent.admin.service.AdminTerminologyService;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.chat.flow.query.pipeline.QueryPipelineConfigService;
import com.yinbo.agent.chat.flow.query.pipeline.QueryPipelineConfigService.UpdateQueryPipelineConfigCommand;
import com.yinbo.agent.chat.flow.query.pipeline.QueryPipelineConfigView;
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
@RequestMapping("/api/admin/query")
// 查询预处理后台管理接口。
public class AdminQueryPipelineController {

    private final AdminGuard adminGuard;
    private final AdminTerminologyService terminologyService;
    private final QueryPipelineConfigService pipelineConfigService;

    // 注入管理员校验、术语管理和流水线配置服务。
    public AdminQueryPipelineController(
            AdminGuard adminGuard,
            AdminTerminologyService terminologyService,
            QueryPipelineConfigService pipelineConfigService
    ) {
        this.adminGuard = adminGuard;
        this.terminologyService = terminologyService;
        this.pipelineConfigService = pipelineConfigService;
    }

    @GetMapping("/terminology/mappings")
    // 查询关键词映射列表。
    public List<TerminologyMappingResponse> mappings(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return terminologyService.listMappings();
    }

    @PostMapping("/terminology/mappings")
    // 新增关键词映射。
    public TerminologyMappingResponse createMapping(
            HttpServletRequest request,
            @Valid @RequestBody TerminologyMappingRequest mappingRequest
    ) {
        adminGuard.requireAdmin(request);
        return terminologyService.createMapping(mappingRequest);
    }

    @PatchMapping("/terminology/mappings/{aliasId}")
    // 修改关键词映射。
    public TerminologyMappingResponse updateMapping(
            HttpServletRequest request,
            @PathVariable Long aliasId,
            @Valid @RequestBody TerminologyMappingRequest mappingRequest
    ) {
        adminGuard.requireAdmin(request);
        return terminologyService.updateMapping(aliasId, mappingRequest);
    }

    @PatchMapping("/terminology/mappings/{aliasId}/enabled")
    // 启用或禁用关键词映射。
    public TerminologyMappingResponse updateMappingEnabled(
            HttpServletRequest request,
            @PathVariable Long aliasId,
            @RequestBody MappingEnabledRequest enabledRequest
    ) {
        adminGuard.requireAdmin(request);
        return terminologyService.updateMappingEnabled(aliasId, Boolean.TRUE.equals(enabledRequest.enabled()));
    }

    @DeleteMapping("/terminology/mappings/{aliasId}")
    // 删除关键词映射。
    public void deleteMapping(HttpServletRequest request, @PathVariable Long aliasId) {
        adminGuard.requireAdmin(request);
        terminologyService.deleteMapping(aliasId);
    }

    @GetMapping("/pipeline/config")
    // 查询查询预处理流水线配置。
    public QueryPipelineConfigView pipelineConfig(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return pipelineConfigService.currentConfig();
    }

    @PatchMapping("/pipeline/config")
    // 更新查询预处理流水线配置。
    public QueryPipelineConfigView updatePipelineConfig(
            HttpServletRequest request,
            @Valid @RequestBody UpdateQueryPipelineConfigRequest configRequest
    ) {
        AuthUser authUser = adminGuard.requireAdmin(request);
        return pipelineConfigService.update(
                new UpdateQueryPipelineConfigCommand(
                        configRequest.terminologyEnabled(),
                        configRequest.llmRewriteEnabled(),
                        configRequest.ruleSplitEnabled(),
                        configRequest.fallbackPolicy(),
                        configRequest.rewriteTimeoutMs(),
                        configRequest.rewriteContextTurns()
                ),
                authUser.getId()
        );
    }

    // 启用状态请求。
    public record MappingEnabledRequest(Boolean enabled) {
    }
}
