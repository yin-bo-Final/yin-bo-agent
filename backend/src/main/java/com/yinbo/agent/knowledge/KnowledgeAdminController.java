package com.yinbo.agent.knowledge;

import com.yinbo.agent.admin.AdminGuard;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.ingestion.DocumentIngestionService;
import com.yinbo.agent.ingestion.dto.IngestionResponse;
import com.yinbo.agent.knowledge.dto.ChunkEnabledRequest;
import com.yinbo.agent.knowledge.dto.CreateKnowledgeBaseRequest;
import com.yinbo.agent.knowledge.dto.KnowledgeBaseResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeChunkResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeDocumentResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeOverviewResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeUrlIngestionRequest;
import com.yinbo.agent.knowledge.entity.KnowledgeBase;
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
public class KnowledgeAdminController {

    private final AdminGuard adminGuard;
    private final KnowledgeAdminService knowledgeAdminService;
    private final DocumentIngestionService documentIngestionService;

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
    public KnowledgeOverviewResponse overview(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.overview();
    }

    @GetMapping("/bases")
    public List<KnowledgeBaseResponse> listKnowledgeBases(HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.list();
    }

    @PostMapping("/bases")
    public KnowledgeBaseResponse createKnowledgeBase(
            @Valid @RequestBody CreateKnowledgeBaseRequest createRequest,
            HttpServletRequest request
    ) {
        AuthUser adminUser = adminGuard.requireAdmin(request);
        return knowledgeAdminService.create(adminUser, createRequest);
    }

    @GetMapping("/bases/{knowledgeBaseId}/documents")
    public List<KnowledgeDocumentResponse> listDocuments(
            @PathVariable String knowledgeBaseId,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.listDocuments(knowledgeBaseId);
    }

    @PostMapping(path = "/bases/{knowledgeBaseId}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    @GetMapping("/documents/{documentId}/chunks")
    public List<KnowledgeChunkResponse> listChunks(
            @PathVariable String documentId,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.listChunks(documentId);
    }

    @PatchMapping("/documents/{documentId}/chunks/enabled")
    public List<KnowledgeChunkResponse> updateDocumentChunksEnabled(
            @PathVariable String documentId,
            @RequestBody ChunkEnabledRequest enabledRequest,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.updateDocumentChunksEnabled(documentId, enabledRequest);
    }

    @PatchMapping("/chunks/{chunkId}/enabled")
    public KnowledgeChunkResponse updateChunkEnabled(
            @PathVariable String chunkId,
            @RequestBody ChunkEnabledRequest enabledRequest,
            HttpServletRequest request
    ) {
        adminGuard.requireAdmin(request);
        return knowledgeAdminService.updateChunkEnabled(chunkId, enabledRequest);
    }

    @DeleteMapping("/chunks/{chunkId}")
    public void deleteChunk(@PathVariable String chunkId, HttpServletRequest request) {
        adminGuard.requireAdmin(request);
        knowledgeAdminService.deleteChunk(chunkId);
    }
}
