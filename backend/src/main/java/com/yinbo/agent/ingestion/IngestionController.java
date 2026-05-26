package com.yinbo.agent.ingestion;

import com.yinbo.agent.admin.AdminGuard;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.ingestion.dto.IngestionResponse;
import com.yinbo.agent.ingestion.dto.UrlIngestionRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final AdminGuard adminGuard;
    private final DocumentIngestionService documentIngestionService;

    public IngestionController(AdminGuard adminGuard, DocumentIngestionService documentIngestionService) {
        this.adminGuard = adminGuard;
        this.documentIngestionService = documentIngestionService;
    }

    @PostMapping(path = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestionResponse uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "strategy", required = false) String strategy,
            @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap,
            @RequestParam(value = "maxChunks", required = false) Integer maxChunks,
            HttpServletRequest httpRequest
    ) {
        AuthUser authUser = adminGuard.requireAdmin(httpRequest);
        return documentIngestionService.ingestUpload(authUser, file, strategy, chunkSize, chunkOverlap, maxChunks);
    }

    @PostMapping("/documents/url")
    public IngestionResponse ingestUrl(
            @Valid @RequestBody UrlIngestionRequest request,
            HttpServletRequest httpRequest
    ) {
        AuthUser authUser = adminGuard.requireAdmin(httpRequest);
        return documentIngestionService.ingestUrl(
                authUser,
                request.url(),
                request.fileName(),
                request.strategy(),
                request.chunkSize(),
                request.chunkOverlap(),
                request.maxChunks()
        );
    }
}
