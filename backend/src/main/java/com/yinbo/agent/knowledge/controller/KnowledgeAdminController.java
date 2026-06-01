package com.yinbo.agent.knowledge.controller;

import com.yinbo.agent.admin.AdminGuard;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.ingestion.service.DocumentIngestionService;
import com.yinbo.agent.ingestion.dto.IngestionResponse;
import com.yinbo.agent.knowledge.dto.ChunkEnabledRequest;
import com.yinbo.agent.knowledge.dto.CreateKnowledgeBaseRequest;
import com.yinbo.agent.knowledge.dto.KnowledgeBaseResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeChunkResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeDocumentResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeOverviewResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeUrlIngestionRequest;
import com.yinbo.agent.knowledge.dto.RechunkDocumentRequest;
import com.yinbo.agent.knowledge.dto.UpdateChunkRequest;
import com.yinbo.agent.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.yinbo.agent.knowledge.entity.KnowledgeBase;
import com.yinbo.agent.knowledge.service.KnowledgeAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/knowledge")
// 管理后台知识库接口。
public class KnowledgeAdminController {

    private final AdminGuard adminGuard;
    private final KnowledgeAdminService knowledgeAdminService;
    private final DocumentIngestionService documentIngestionService;

    // 注入管理员校验、知识库管理和文档入库服务。
    public KnowledgeAdminController(
            AdminGuard adminGuard,
            KnowledgeAdminService knowledgeAdminService,
            DocumentIngestionService documentIngestionService
    ) {
        this.adminGuard = adminGuard;
        this.knowledgeAdminService = knowledgeAdminService;
        this.documentIngestionService = documentIngestionService;
    }

    @GetMapping("/overview")
    // 查询知识库后台概览统计。
    public KnowledgeOverviewResponse overview(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.overview();
    }

    @GetMapping("/bases")
    // 查询知识库列表。
    public List<KnowledgeBaseResponse> listKnowledgeBases(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.list();
    }

    @PostMapping("/bases")
    // 创建知识库。
    public KnowledgeBaseResponse createKnowledgeBase(
            @Valid @RequestBody CreateKnowledgeBaseRequest createRequest,
            HttpServletRequest request
    ) {
        AuthUser adminUser = adminGuard.requireAdmin(request);
        return knowledgeAdminService.create(adminUser, createRequest);
    }

    @GetMapping("/bases/{knowledgeBaseId}")
    // 查询知识库详情。
    public KnowledgeBaseResponse knowledgeBaseDetail(
            @PathVariable String knowledgeBaseId,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.detail(knowledgeBaseId);
    }

    @DeleteMapping("/bases/{knowledgeBaseId}")
    // 删除知识库。
    public void deleteKnowledgeBase(
            @PathVariable String knowledgeBaseId,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        knowledgeAdminService.deleteKnowledgeBase(knowledgeBaseId);
    }

    @PatchMapping("/bases/{knowledgeBaseId}")
    // 更新知识库基础信息。
    public KnowledgeBaseResponse updateKnowledgeBase(
            @PathVariable String knowledgeBaseId,
            @Valid @RequestBody UpdateKnowledgeBaseRequest updateRequest,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.updateKnowledgeBase(knowledgeBaseId, updateRequest);
    }

    @GetMapping("/bases/{knowledgeBaseId}/documents")
    // 查询知识库下的文档列表。
    public List<KnowledgeDocumentResponse> listDocuments(
            @PathVariable String knowledgeBaseId,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.listDocuments(knowledgeBaseId);
    }

    @PostMapping(path = "/bases/{knowledgeBaseId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    // 向指定知识库上传文档。
    public IngestionResponse uploadDocument(
            @PathVariable String knowledgeBaseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "strategy", required = false) String strategy,
            @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap,
            @RequestParam(value = "maxChunks", required = false) Integer maxChunks,
            HttpServletRequest request
    ) {
        AuthUser adminUser = adminGuard.requireAdmin(request);
        KnowledgeBase knowledgeBase = knowledgeAdminService.requireKnowledgeBase(knowledgeBaseId);
        return documentIngestionService.ingestUpload(
                adminUser,
                knowledgeBase,
                file,
                strategy,
                chunkSize,
                chunkOverlap,
                maxChunks
        );
    }

    @PostMapping("/bases/{knowledgeBaseId}/documents/url")
    // 向指定知识库录入 URL 文档。
    public IngestionResponse ingestUrl(
            @PathVariable String knowledgeBaseId,
            @Valid @RequestBody KnowledgeUrlIngestionRequest ingestionRequest,
            HttpServletRequest request
    ) {
        AuthUser adminUser = adminGuard.requireAdmin(request);
        KnowledgeBase knowledgeBase = knowledgeAdminService.requireKnowledgeBase(knowledgeBaseId);
        return documentIngestionService.ingestUrl(
                adminUser,
                knowledgeBase,
                ingestionRequest.url(),
                ingestionRequest.fileName(),
                ingestionRequest.strategy(),
                ingestionRequest.chunkSize(),
                ingestionRequest.chunkOverlap(),
                ingestionRequest.maxChunks()
        );
    }

    @GetMapping("/documents/{documentId}")
    // 查询文档详情。
    public KnowledgeDocumentResponse documentDetail(
            @PathVariable String documentId,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.documentDetail(documentId);
    }

    @GetMapping("/documents/{documentId}/chunks")
    // 查询文档分块列表。
    public List<KnowledgeChunkResponse> listChunks(
            @PathVariable String documentId,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.listChunks(documentId);
    }

    @PostMapping("/documents/{documentId}/rechunk")
    // 投递文档重新分块任务。
    public KnowledgeDocumentResponse rechunkDocument(
            @PathVariable String documentId,
            @RequestBody(required = false) RechunkDocumentRequest rechunkRequest,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.rechunkDocument(documentId, rechunkRequest);
    }

    @PostMapping("/documents/{documentId}/vectors/rebuild")
    // 投递文档向量重建任务。
    public KnowledgeDocumentResponse rebuildDocumentVectors(
            @PathVariable String documentId,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.rebuildDocumentVectors(documentId);
    }

    @PatchMapping("/documents/{documentId}/chunks/enabled")
    // 批量更新文档分块启用状态。
    public List<KnowledgeChunkResponse> updateDocumentChunksEnabled(
            @PathVariable String documentId,
            @RequestBody ChunkEnabledRequest enabledRequest,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.updateDocumentChunksEnabled(documentId, enabledRequest);
    }

    @DeleteMapping("/documents/{documentId}")
    // 删除指定文档。
    public void deleteDocument(@PathVariable String documentId, HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        knowledgeAdminService.deleteDocument(documentId);
    }

    @PatchMapping("/chunks/{chunkId}/enabled")
    // 更新单个分块启用状态。
    public KnowledgeChunkResponse updateChunkEnabled(
            @PathVariable String chunkId,
            @RequestBody ChunkEnabledRequest enabledRequest,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.updateChunkEnabled(chunkId, enabledRequest);
    }

    @PatchMapping("/chunks/{chunkId}")
    // 更新单个分块内容。
    public KnowledgeChunkResponse updateChunk(
            @PathVariable String chunkId,
            @Valid @RequestBody UpdateChunkRequest updateRequest,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.updateChunk(chunkId, updateRequest);
    }

    @DeleteMapping("/chunks/{chunkId}")
    // 删除单个分块。
    public void deleteChunk(@PathVariable String chunkId, HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        knowledgeAdminService.deleteChunk(chunkId);
    }
}
