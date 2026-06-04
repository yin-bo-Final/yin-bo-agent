package com.yinbo.agent.ingestion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.ai.api.embedding.EmbeddingService;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.common.service.RedisSemaphoreService;
import com.yinbo.agent.config.ConcurrencyLimitProperties;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.model.ChunkingOptions;
import com.yinbo.agent.ingestion.model.ChunkingStrategy;
import com.yinbo.agent.ingestion.model.DocumentChunk;
import com.yinbo.agent.ingestion.model.DocumentSourceType;
import com.yinbo.agent.ingestion.model.IngestionExecutionResult;
import com.yinbo.agent.ingestion.model.ParsedDocument;
import com.yinbo.agent.ingestion.model.RawDocument;
import com.yinbo.agent.ingestion.cleaner.DocumentTextCleaner;
import com.yinbo.agent.ingestion.dto.IngestionResponse;
import com.yinbo.agent.ingestion.entity.KnowledgeChunk;
import com.yinbo.agent.ingestion.entity.KnowledgeDocument;
import com.yinbo.agent.ingestion.mapper.KnowledgeChunkMapper;
import com.yinbo.agent.ingestion.mapper.KnowledgeDocumentMapper;
import com.yinbo.agent.ingestion.optimizer.DocumentChunkOptimizer;
import com.yinbo.agent.ingestion.parser.TikaDocumentParser;
import com.yinbo.agent.ingestion.source.DocumentSourceReader;
import com.yinbo.agent.ingestion.splitter.RecursiveDocumentChunkSplitter;
import com.yinbo.agent.ingestion.vector.PgVectorRepository;
import com.yinbo.agent.ingestion.vector.PgVectorRow;
import com.yinbo.agent.knowledge.entity.KnowledgeBase;
import com.yinbo.agent.knowledge.mapper.KnowledgeBaseMapper;
import com.yinbo.agent.storage.service.ObjectStorageService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
// 文档入库核心业务服务。
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String UPLOAD_SEMAPHORE_NAME = "service:ingestion:upload:global";

    private final RagProperties ragProperties;
    private final ConcurrencyLimitProperties concurrencyLimitProperties;
    private final RedisSemaphoreService redisSemaphoreService;
    private final EmbeddingService embeddingService;
    private final PgVectorRepository pgVectorRepository;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final DocumentSourceReader documentSourceReader;
    private final TikaDocumentParser tikaDocumentParser;
    private final DocumentTextCleaner documentTextCleaner;
    private final RecursiveDocumentChunkSplitter chunkSplitter;
    private final DocumentChunkOptimizer chunkOptimizer;
    private final ObjectStorageService objectStorageService;
    private final TransactionTemplate transactionTemplate;

    // 注入文档入库、对象存储、向量存储和事务相关依赖。
    public DocumentIngestionService(
            RagProperties ragProperties,
            ConcurrencyLimitProperties concurrencyLimitProperties,
            RedisSemaphoreService redisSemaphoreService,
            EmbeddingService embeddingService,
            PgVectorRepository pgVectorRepository,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper,
            DocumentSourceReader documentSourceReader,
            TikaDocumentParser tikaDocumentParser,
            DocumentTextCleaner documentTextCleaner,
            RecursiveDocumentChunkSplitter chunkSplitter,
            DocumentChunkOptimizer chunkOptimizer,
            ObjectStorageService objectStorageService,
            TransactionTemplate transactionTemplate
    ) {
        this.ragProperties = ragProperties;
        this.concurrencyLimitProperties = concurrencyLimitProperties;
        this.redisSemaphoreService = redisSemaphoreService;
        this.embeddingService = embeddingService;
        this.pgVectorRepository = pgVectorRepository;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.documentSourceReader = documentSourceReader;
        this.tikaDocumentParser = tikaDocumentParser;
        this.documentTextCleaner = documentTextCleaner;
        this.chunkSplitter = chunkSplitter;
        this.chunkOptimizer = chunkOptimizer;
        this.objectStorageService = objectStorageService;
        this.transactionTemplate = transactionTemplate;
    }

    // 上传文件并创建默认知识库外的待处理文档。
    public IngestionResponse ingestUpload(
            AuthUser authUser,
            MultipartFile file,
            String strategy,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer maxChunks
    ) {
        return withUploadPermit(authUser, null, file, () -> {
            ChunkingOptions options = ChunkingOptions.from(ragProperties, strategy, chunkSize, chunkOverlap, maxChunks);
            return createUploadDocument(authUser, null, file, options);
        });
    }

    // 上传文件并创建指定知识库下的待处理文档。
    public IngestionResponse ingestUpload(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            MultipartFile file,
            String strategy,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer maxChunks
    ) {
        return withUploadPermit(authUser, knowledgeBase, file, () -> {
            ChunkingOptions options = ChunkingOptions.from(ragProperties, strategy, chunkSize, chunkOverlap, maxChunks);
            return createUploadDocument(authUser, knowledgeBase, file, options);
        });
    }

    // 录入 URL 并创建默认知识库外的待处理文档。
    public IngestionResponse ingestUrl(
            AuthUser authUser,
            String url,
            String fileName,
            String strategy,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer maxChunks
    ) {
        RawDocument rawDocument = documentSourceReader.fromUrl(url, fileName);
        ChunkingOptions options = ChunkingOptions.from(ragProperties, strategy, chunkSize, chunkOverlap, maxChunks);
        return createUploadedDocument(authUser, null, rawDocument, options);
    }

    // 录入 URL 并创建指定知识库下的待处理文档。
    public IngestionResponse ingestUrl(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            String url,
            String fileName,
            String strategy,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer maxChunks
    ) {
        RawDocument rawDocument = documentSourceReader.fromUrl(url, fileName);
        ChunkingOptions options = ChunkingOptions.from(ragProperties, strategy, chunkSize, chunkOverlap, maxChunks);
        return createUploadedDocument(authUser, knowledgeBase, rawDocument, options);
    }

    // 创建已上传状态的文档记录。
    private IngestionResponse createUploadedDocument(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            RawDocument rawDocument,
            ChunkingOptions options
    ) {
        try {
            KnowledgeDocument document = createDocument(authUser, knowledgeBase, rawDocument, options, STATUS_UPLOADED);
            log.info(
                    "event=document_uploaded userId={} knowledgeBaseId={} documentId={} sourceType={} fileName={} sizeBytes={} strategy={}",
                    authUser.getId(),
                    knowledgeBase == null ? null : knowledgeBase.getKnowledgeBaseNo(),
                    document.getDocumentNo(),
                    document.getSourceType(),
                    sanitizeLogValue(document.getFileName()),
                    document.getOriginalSizeBytes(),
                    options.strategy().name()
            );
            return toResponse(document);
        } catch (RuntimeException exception) {
            deleteRawDocumentQuietly(rawDocument);
            throw exception;
        }
    }

    // 创建上传中文档记录，文件保存成功后改为已上传。
    private IngestionResponse createUploadDocument(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            MultipartFile file,
            ChunkingOptions options
    ) {
        validateUploadFile(file);
        KnowledgeDocument document = createDocument(
                authUser,
                knowledgeBase,
                toUploadingRawDocument(file),
                options,
                STATUS_UPLOADING
        );
        RawDocument storedRawDocument = null;
        try {
            storedRawDocument = documentSourceReader.fromUpload(file);
            applyStoredRawDocument(document, storedRawDocument);
            document.setStatus(STATUS_UPLOADED);
            document.setErrorMessage(null);
            knowledgeDocumentMapper.updateById(document);
            log.info(
                    "event=document_uploaded userId={} knowledgeBaseId={} documentId={} sourceType={} fileName={} sizeBytes={} strategy={}",
                    authUser.getId(),
                    knowledgeBase == null ? null : knowledgeBase.getKnowledgeBaseNo(),
                    document.getDocumentNo(),
                    document.getSourceType(),
                    sanitizeLogValue(document.getFileName()),
                    document.getOriginalSizeBytes(),
                    options.strategy().name()
            );
            return toResponse(document);
        } catch (RuntimeException exception) {
            deleteRawDocumentQuietly(storedRawDocument);
            markUploadFailed(document, exception);
            throw exception;
        }
    }

    // 校验上传文件基础约束。
    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请上传一个非空文件");
        }
        if (file.getSize() > ragProperties.maxSourceBytes()) {
            throw new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "文件大小不能超过 200MB");
        }
    }

    // 创建上传中状态使用的原始文档信息。
    private RawDocument toUploadingRawDocument(MultipartFile file) {
        return new RawDocument(
                DocumentSourceType.UPLOAD,
                null,
                sanitizeFileName(file.getOriginalFilename(), "uploaded-document"),
                file.getContentType(),
                file.getSize(),
                null,
                null,
                null,
                null,
                null
        );
    }

    // 把对象存储结果回填到文档记录。
    private void applyStoredRawDocument(KnowledgeDocument document, RawDocument rawDocument) {
        document.setSourceType(rawDocument.sourceType().name());
        document.setSourceUrl(rawDocument.sourceUrl());
        document.setFileName(rawDocument.fileName());
        document.setContentType(rawDocument.contentType());
        document.setOriginalSizeBytes(rawDocument.sizeBytes());
        document.setStorageProvider(rawDocument.storageProvider());
        document.setStorageBucket(rawDocument.storageBucket());
        document.setStorageObjectKey(rawDocument.storageObjectKey());
        document.setStorageEtag(rawDocument.storageEtag());
    }

    // 上传失败时保留失败文档，便于后台排查。
    private void markUploadFailed(KnowledgeDocument document, RuntimeException exception) {
        document.setStatus(STATUS_FAILED);
        document.setErrorMessage(truncate("文件上传失败：" + conciseMessage(exception), 1000));
        try {
            knowledgeDocumentMapper.updateById(document);
        } catch (RuntimeException updateException) {
            log.warn(
                    "event=upload_status_update_failed documentId={} type={} message={}",
                    document.getDocumentNo(),
                    updateException.getClass().getSimpleName(),
                    sanitizeLogValue(updateException.getMessage()),
                    updateException
            );
        }
    }

    // 在上传并发许可保护下执行上传动作。
    private IngestionResponse withUploadPermit(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            MultipartFile file,
            Supplier<IngestionResponse> uploadAction
    ) {
        ConcurrencyLimitProperties.Limit limit = concurrencyLimitProperties.upload();
        RedisSemaphoreService.Permit permit = acquireUploadPermit(authUser, knowledgeBase, file, limit);
        long startedAt = System.nanoTime();
        try (permit) {
            IngestionResponse response = uploadAction.get();
            log.info(
                    "event=upload_concurrency_completed userId={} knowledgeBaseId={} documentId={} maxPermits={} costMs={}",
                    authUser.getId(),
                    knowledgeBase == null ? null : knowledgeBase.getKnowledgeBaseNo(),
                    response.documentId(),
                    limit.maxPermits(),
                    elapsedMillis(startedAt)
            );
            return response;
        } catch (RuntimeException exception) {
            log.warn(
                    "event=upload_concurrency_failed userId={} knowledgeBaseId={} fileName={} maxPermits={} costMs={} type={} message={}",
                    authUser.getId(),
                    knowledgeBase == null ? null : knowledgeBase.getKnowledgeBaseNo(),
                    sanitizeLogValue(file == null ? null : file.getOriginalFilename()),
                    limit.maxPermits(),
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage())
            );
            throw exception;
        }
    }

    // 获取 service 层上传并发许可。
    private RedisSemaphoreService.Permit acquireUploadPermit(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            MultipartFile file,
            ConcurrencyLimitProperties.Limit limit
    ) {
        try {
            return redisSemaphoreService.tryAcquire(
                            UPLOAD_SEMAPHORE_NAME,
                            limit.maxPermits(),
                            limit.leaseTtl()
                    )
                    .orElseThrow(() -> {
                        log.warn(
                                "event=upload_concurrency_limited userId={} knowledgeBaseId={} fileName={} maxPermits={}",
                                authUser.getId(),
                                knowledgeBase == null ? null : knowledgeBase.getKnowledgeBaseNo(),
                                sanitizeLogValue(file == null ? null : file.getOriginalFilename()),
                                limit.maxPermits()
                        );
                        return new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "当前上传任务较多，请稍后再试");
                    });
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "event=upload_concurrency_unavailable userId={} knowledgeBaseId={} fileName={} maxPermits={} type={} message={}",
                    authUser.getId(),
                    knowledgeBase == null ? null : knowledgeBase.getKnowledgeBaseNo(),
                    sanitizeLogValue(file == null ? null : file.getOriginalFilename()),
                    limit.maxPermits(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "上传并发控制暂时不可用，请稍后再试");
        }
    }

    // 解析、清洗、切块并向量化文档。
    public IngestionExecutionResult processDocument(String documentId, ChunkingOptions options) {
        KnowledgeDocument document = requireDocument(documentId);
        KnowledgeBase knowledgeBase = document.getKnowledgeBaseId() == null
                ? null
                : requireKnowledgeBaseById(document.getKnowledgeBaseId());

        long totalStartedAt = System.nanoTime();
        try {
            log.info(
                    "event=ingestion_started action=CHUNK documentId={} strategy={} chunkSize={} chunkOverlap={} maxChunks={}",
                    document.getDocumentNo(),
                    options.strategy().name(),
                    options.chunkSize(),
                    options.chunkOverlap(),
                    options.maxChunks()
            );
            document.setStatus(STATUS_PROCESSING);
            document.setErrorMessage(null);
            document.setChunkStrategy(options.strategy().name());
            document.setChunkSize(options.chunkSize());
            document.setChunkOverlap(options.chunkOverlap());
            document.setMaxChunks(options.maxChunks());
            knowledgeDocumentMapper.updateById(document);

            RawDocument rawDocument = toRawDocument(document);
            long parseStartedAt = System.nanoTime();
            ParsedDocument parsedDocument = parseDocument(document, rawDocument);
            String cleanText = documentTextCleaner.clean(parsedDocument.text());
            if (cleanText.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "清洗后没有可用文本");
            }
            options = options.adaptForTextLength(cleanText.length());
            long parseDurationMs = elapsedMillis(parseStartedAt);
            document.setTextExtractedAt(LocalDateTime.now());

            long chunkStartedAt = System.nanoTime();
            List<DocumentChunk> chunks = chunkSplitter.split(cleanText, parsedDocument.title(), options);
            chunks = chunkOptimizer.optimize(chunks, options);
            validateChunksForEmbedding(chunks, options);
            if (chunks.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "文档没有生成可入库的切块");
            }
            if (chunks.size() > options.maxChunks()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "优化后切块数量超过上限，请调大 chunkSize 或 maxChunks");
            }

            List<String> vectorDocumentIds = new ArrayList<>();
            List<KnowledgeChunk> chunkEntities = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                String vectorDocumentId = UUID.randomUUID().toString();
                vectorDocumentIds.add(vectorDocumentId);
                chunkEntities.add(toChunkEntity(document, chunk, vectorDocumentId));
            }
            long chunkDurationMs = elapsedMillis(chunkStartedAt);

            long embeddingStartedAt = System.nanoTime();
            List<float[]> embeddings = embedChunks(chunks);
            long embeddingDurationMs = elapsedMillis(embeddingStartedAt);

            List<PgVectorRow> vectorRows = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                vectorRows.add(toVectorRow(
                        document,
                        knowledgeBase,
                        rawDocument,
                        parsedDocument,
                        chunks.get(i),
                        vectorDocumentIds.get(i),
                        embeddings.get(i)
                ));
            }

            long totalDurationMs = elapsedMillis(totalStartedAt);
            ChunkingOptions finalOptions = options;
            int finalChunkCount = chunks.size();
            transactionTemplate.executeWithoutResult(status -> {
                KnowledgeDocument lockedDocument = requireDocumentForUpdate(documentId);
                List<KnowledgeChunk> oldChunks = listChunkEntities(lockedDocument.getId());
                pgVectorRepository.insertAll(vectorRows);
                chunkEntities.forEach(knowledgeChunkMapper::insert);
                deleteChunkEntities(oldChunks);
                pgVectorRepository.deleteByIds(vectorDocumentIds(oldChunks));

                lockedDocument.setTextCharCount(cleanText.length());
                lockedDocument.setTextContent(cleanText);
                lockedDocument.setChunkCount(finalChunkCount);
                lockedDocument.setStatus(STATUS_COMPLETED);
                lockedDocument.setErrorMessage(null);
                lockedDocument.setChunkStrategy(finalOptions.strategy().name());
                lockedDocument.setChunkSize(finalOptions.chunkSize());
                lockedDocument.setChunkOverlap(finalOptions.chunkOverlap());
                lockedDocument.setMaxChunks(finalOptions.maxChunks());
                lockedDocument.setTextExtractedAt(document.getTextExtractedAt());
                lockedDocument.setParseDurationMs(parseDurationMs);
                lockedDocument.setChunkDurationMs(chunkDurationMs);
                lockedDocument.setEmbeddingDurationMs(embeddingDurationMs);
                lockedDocument.setTotalDurationMs(totalDurationMs);
                lockedDocument.setOtherDurationMs(Math.max(0, totalDurationMs - parseDurationMs - chunkDurationMs - embeddingDurationMs));
                knowledgeDocumentMapper.updateById(lockedDocument);
            });
            log.info(
                    "event=ingestion_completed action=CHUNK documentId={} knowledgeBaseId={} chunkCount={} textChars={} parseMs={} chunkMs={} embeddingMs={} totalMs={}",
                    document.getDocumentNo(),
                    knowledgeBase == null ? null : knowledgeBase.getKnowledgeBaseNo(),
                    chunks.size(),
                    cleanText.length(),
                    parseDurationMs,
                    chunkDurationMs,
                    embeddingDurationMs,
                    totalDurationMs
            );
            return IngestionExecutionResult.succeeded();
        } catch (RuntimeException exception) {
            boolean retryable = isRetryableFailure(exception);
            if (!retryable) {
                markFailed(document, exception);
            }
            log.warn(
                    "event=ingestion_failed action=CHUNK documentId={} retryable={} type={} message={}",
                    document.getDocumentNo(),
                    retryable,
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            return IngestionExecutionResult.failure(retryable, conciseMessage(exception));
        }
    }

    // 重建已有文档分块的向量。
    public IngestionExecutionResult rebuildDocumentVectors(String documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        KnowledgeBase knowledgeBase = document.getKnowledgeBaseId() == null
                ? null
                : requireKnowledgeBaseById(document.getKnowledgeBaseId());

        List<KnowledgeChunk> chunks = listChunkEntities(document.getId());
        if (chunks.isEmpty()) {
            log.warn("event=ingestion_failed action=REBUILD_VECTORS documentId={} reason=no_chunks", documentId);
            markFailed(document, new BusinessException(HttpStatus.BAD_REQUEST, "文档没有可重建的分块"));
            return IngestionExecutionResult.failure(false, "文档没有可重建的分块");
        }

        List<String> oldVectorDocumentIds = vectorDocumentIds(chunks);
        try {
            log.info(
                    "event=ingestion_started action=REBUILD_VECTORS documentId={} chunkCount={}",
                    document.getDocumentNo(),
                    chunks.size()
            );
            RawDocument rawDocument = toRawDocument(document);
            ParsedDocument parsedDocument = new ParsedDocument(
                    document.getTextContent() == null ? "" : document.getTextContent(),
                    documentTitle(document),
                    Map.of()
            );
            List<DocumentChunk> documentChunks = chunks.stream()
                    .map(chunk -> new DocumentChunk(
                            chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex(),
                            chunk.getTitle(),
                            chunk.getContent()
                    ))
                    .toList();
            long embeddingStartedAt = System.nanoTime();
            List<float[]> embeddings = embedTexts(documentChunks.stream().map(DocumentChunk::content).toList());
            long embeddingDurationMs = elapsedMillis(embeddingStartedAt);
            List<PgVectorRow> vectorRows = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk chunk = chunks.get(i);
                DocumentChunk documentChunk = documentChunks.get(i);
                String vectorDocumentId = UUID.randomUUID().toString();
                chunk.setVectorDocumentId(vectorDocumentId);
                vectorRows.add(toVectorRow(
                        document,
                        knowledgeBase,
                        rawDocument,
                        parsedDocument,
                        documentChunk,
                        vectorDocumentId,
                        embeddings.get(i)
                ));
            }
            transactionTemplate.executeWithoutResult(status -> {
                KnowledgeDocument lockedDocument = requireDocumentForUpdate(documentId);
                lockedDocument.setStatus(STATUS_PROCESSING);
                lockedDocument.setErrorMessage(null);
                pgVectorRepository.insertAll(vectorRows);
                chunks.forEach(knowledgeChunkMapper::updateById);
                pgVectorRepository.deleteByIds(oldVectorDocumentIds);

                lockedDocument.setStatus(STATUS_COMPLETED);
                lockedDocument.setErrorMessage(null);
                lockedDocument.setEmbeddingDurationMs(embeddingDurationMs);
                refreshDurationSummary(lockedDocument);
                knowledgeDocumentMapper.updateById(lockedDocument);
            });
            log.info(
                    "event=ingestion_completed action=REBUILD_VECTORS documentId={} chunkCount={} embeddingMs={} totalMs={}",
                    document.getDocumentNo(),
                    chunks.size(),
                    embeddingDurationMs,
                    nullToZero(document.getParseDurationMs()) + nullToZero(document.getChunkDurationMs()) + embeddingDurationMs + nullToZero(document.getOtherDurationMs())
            );
            return IngestionExecutionResult.succeeded();
        } catch (RuntimeException exception) {
            boolean retryable = isRetryableFailure(exception);
            if (!retryable) {
                markFailed(document, exception);
            }
            log.warn(
                    "event=ingestion_failed action=REBUILD_VECTORS documentId={} retryable={} type={} message={}",
                    document.getDocumentNo(),
                    retryable,
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            return IngestionExecutionResult.failure(retryable, conciseMessage(exception));
        }
    }

    // 将文档标记为最终失败。
    public void markDocumentFailed(String documentId, String message) {
        KnowledgeDocument document = requireDocument(documentId);
        markFailed(document, new BusinessException(HttpStatus.BAD_GATEWAY, message));
    }

    // 对分块文本生成向量，远程调用保持在数据库事务外。
    private List<float[]> embedChunks(List<DocumentChunk> chunks) {
        return embedTexts(chunks.stream().map(DocumentChunk::content).toList());
    }

    // 对文本列表生成向量。
    private List<float[]> embedTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        try {
            List<float[]> embeddings = embeddingService.embedBatch(texts, ragProperties.embeddingModel());
            if (embeddings.size() != texts.size()) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "Embedding 返回数量和分块数量不一致");
            }
            return embeddings;
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(
                    HttpStatus.BAD_GATEWAY,
                    "向量化失败，请检查 Embedding 服务或切块大小：" + conciseMessage(exception)
            );
        }
    }

    // 校验分块是否适合 Embedding。
    private void validateChunksForEmbedding(List<DocumentChunk> chunks, ChunkingOptions options) {
        for (DocumentChunk chunk : chunks) {
            if (chunk.content().length() <= ChunkingOptions.MAX_EMBEDDING_CHUNK_CHARS) {
                continue;
            }
            if (options.strategy() == ChunkingStrategy.NONE) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "不分块仅适合短文本，当前文档单块过大，请改用自动策略或递归切块"
                );
            }
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "单个切块过大，可能超过 Embedding 模型上下文限制，请调小 chunkSize 或改用自动策略"
            );
        }
    }

    // 解析文档文本。
    private ParsedDocument parseDocument(KnowledgeDocument document, RawDocument rawDocument) {
        if (rawDocument.hasStoredObject()) {
            return tikaDocumentParser.parse(rawDocument);
        }
        if (document.getTextContent() != null && !document.getTextContent().isBlank()) {
            return new ParsedDocument(document.getTextContent(), documentTitle(document), Map.of());
        }
        String chunkText = String.join("\n\n", listChunkEntities(document.getId()).stream()
                .map(KnowledgeChunk::getContent)
                .filter(value -> value != null && !value.isBlank())
                .toList());
        if (!chunkText.isBlank()) {
            return new ParsedDocument(chunkText, documentTitle(document), Map.of());
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "原始文件不存在，请重新上传");
    }

    // 将文档实体还原为原始文档描述。
    private RawDocument toRawDocument(KnowledgeDocument document) {
        DocumentSourceType sourceType;
        try {
            sourceType = DocumentSourceType.valueOf(document.getSourceType());
        } catch (RuntimeException exception) {
            sourceType = DocumentSourceType.UPLOAD;
        }
        return new RawDocument(
                sourceType,
                document.getSourceUrl(),
                document.getFileName(),
                document.getContentType(),
                nullToZero(document.getOriginalSizeBytes()),
                null,
                document.getStorageProvider(),
                document.getStorageBucket(),
                document.getStorageObjectKey(),
                document.getStorageEtag()
        );
    }

    // 根据业务编号获取文档。
    private KnowledgeDocument requireDocument(String documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDocumentNo, documentId)
                .last("LIMIT 1"));
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    // 在当前事务内锁定文档行，避免并发任务同时提交分块结果。
    private KnowledgeDocument requireDocumentForUpdate(String documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDocumentNo, documentId)
                .last("FOR UPDATE"));
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    // 根据主键获取知识库。
    private KnowledgeBase requireKnowledgeBaseById(Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "知识库不存在");
        }
        return knowledgeBase;
    }

    private List<KnowledgeChunk> listChunkEntities(Long documentId) {
        return knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
    }

    private void deleteChunkEntities(List<KnowledgeChunk> chunks) {
        List<Long> chunkIds = chunks == null ? List.of() : chunks.stream()
                .map(KnowledgeChunk::getId)
                .filter(id -> id != null)
                .toList();
        if (!chunkIds.isEmpty()) {
            knowledgeChunkMapper.deleteBatchIds(chunkIds);
        }
    }

    private List<String> vectorDocumentIds(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.stream()
                .map(KnowledgeChunk::getVectorDocumentId)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String documentTitle(KnowledgeDocument document) {
        String fileName = document.getFileName();
        if (fileName == null || fileName.isBlank()) {
            return "未命名文档";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private KnowledgeDocument createDocument(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            RawDocument rawDocument,
            ChunkingOptions options,
            String status
    ) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setDocumentNo(UUID.randomUUID().toString());
        document.setKnowledgeBaseId(knowledgeBase == null ? null : knowledgeBase.getId());
        document.setUserId(authUser.getId());
        document.setSourceType(rawDocument.sourceType().name());
        document.setSourceUrl(rawDocument.sourceUrl());
        document.setFileName(rawDocument.fileName());
        document.setContentType(rawDocument.contentType());
        document.setParser(tikaDocumentParser.parserName());
        document.setOriginalSizeBytes(rawDocument.sizeBytes());
        document.setStorageProvider(rawDocument.storageProvider());
        document.setStorageBucket(rawDocument.storageBucket());
        document.setStorageObjectKey(rawDocument.storageObjectKey());
        document.setStorageEtag(rawDocument.storageEtag());
        document.setTextCharCount(0);
        document.setChunkCount(0);
        document.setStatus(status);
        document.setChunkStrategy(options.strategy().name());
        document.setChunkSize(options.chunkSize());
        document.setChunkOverlap(options.chunkOverlap());
        document.setMaxChunks(options.maxChunks());
        document.setParseDurationMs(0L);
        document.setChunkDurationMs(0L);
        document.setEmbeddingDurationMs(0L);
        document.setOtherDurationMs(0L);
        document.setTotalDurationMs(0L);
        knowledgeDocumentMapper.insert(document);
        return document;
    }

    private PgVectorRow toVectorRow(
            KnowledgeDocument document,
            KnowledgeBase knowledgeBase,
            RawDocument rawDocument,
            ParsedDocument parsedDocument,
            DocumentChunk chunk,
            String vectorDocumentId,
            float[] embedding
    ) {
        return new PgVectorRow(
                vectorDocumentId,
                chunk.content(),
                toVectorMetadata(document, knowledgeBase, rawDocument, parsedDocument, chunk),
                embedding
        );
    }

    private Map<String, Object> toVectorMetadata(
            KnowledgeDocument document,
            KnowledgeBase knowledgeBase,
            RawDocument rawDocument,
            ParsedDocument parsedDocument,
            DocumentChunk chunk
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putMetadata(metadata, "document_id", document.getDocumentNo());
        putMetadata(metadata, "document_pk", document.getId());
        if (knowledgeBase != null) {
            putMetadata(metadata, "knowledge_base_id", knowledgeBase.getKnowledgeBaseNo());
            putMetadata(metadata, "knowledge_base_pk", knowledgeBase.getId());
            putMetadata(metadata, "collection_name", knowledgeBase.getCollectionName());
        }
        putMetadata(metadata, "user_id", document.getUserId());
        putMetadata(metadata, "source_type", rawDocument.sourceType().name());
        putMetadata(metadata, "source_url", rawDocument.sourceUrl());
        putMetadata(metadata, "file_name", rawDocument.fileName());
        putMetadata(metadata, "content_type", rawDocument.contentType());
        putMetadata(metadata, "storage_provider", rawDocument.storageProvider());
        putMetadata(metadata, "storage_bucket", rawDocument.storageBucket());
        putMetadata(metadata, "storage_object_key", rawDocument.storageObjectKey());
        putMetadata(metadata, "storage_etag", rawDocument.storageEtag());
        putMetadata(metadata, "parser", tikaDocumentParser.parserName());
        putMetadata(metadata, "embedding_model", ragProperties.embeddingModel());
        putMetadata(metadata, "title", chunk.title());
        putMetadata(metadata, "chunk_index", chunk.index());
        putMetadata(metadata, "ingested_at", Instant.now().toString());
        if (parsedDocument.parserMetadata() != null && !parsedDocument.parserMetadata().isEmpty()) {
            putMetadata(metadata, "parser_metadata", parsedDocument.parserMetadata());
        }
        return metadata;
    }

    private void putMetadata(Map<String, Object> metadata, String key, Object value) {
        if (value == null) {
            return;
        }
        metadata.put(key, value);
    }

    private KnowledgeChunk toChunkEntity(KnowledgeDocument document, DocumentChunk chunk, String vectorDocumentId) {
        KnowledgeChunk chunkEntity = new KnowledgeChunk();
        chunkEntity.setChunkNo(UUID.randomUUID().toString());
        chunkEntity.setDocumentId(document.getId());
        chunkEntity.setUserId(document.getUserId());
        chunkEntity.setVectorDocumentId(vectorDocumentId);
        chunkEntity.setChunkIndex(chunk.index());
        chunkEntity.setTitle(truncate(chunk.title(), 255));
        chunkEntity.setContent(chunk.content());
        chunkEntity.setEnabled(true);
        chunkEntity.setTokenCount(estimateTokenCount(chunk.content()));
        chunkEntity.setCharCount(chunk.content().length());
        return chunkEntity;
    }

    private void refreshDurationSummary(KnowledgeDocument document) {
        long parseDurationMs = nullToZero(document.getParseDurationMs());
        long chunkDurationMs = nullToZero(document.getChunkDurationMs());
        long embeddingDurationMs = nullToZero(document.getEmbeddingDurationMs());
        long otherDurationMs = Math.max(0L, nullToZero(document.getOtherDurationMs()));
        document.setOtherDurationMs(otherDurationMs);
        document.setTotalDurationMs(parseDurationMs + chunkDurationMs + embeddingDurationMs + otherDurationMs);
    }

    private void markFailed(KnowledgeDocument document, RuntimeException exception) {
        document.setStatus(STATUS_FAILED);
        document.setErrorMessage(truncate(exception.getMessage(), 1000));
        try {
            knowledgeDocumentMapper.updateById(document);
        } catch (RuntimeException updateException) {
            log.warn(
                    "event=ingestion_status_update_failed documentId={} type={} message={}",
                    document.getDocumentNo(),
                    updateException.getClass().getSimpleName(),
                    sanitizeLogValue(updateException.getMessage()),
                    updateException
            );
        }
    }

    private void deleteRawDocumentQuietly(RawDocument rawDocument) {
        if (rawDocument == null || !rawDocument.hasStoredObject()) {
            return;
        }
        objectStorageService.deleteQuietly(rawDocument.storageBucket(), rawDocument.storageObjectKey());
    }

    private IngestionResponse toResponse(KnowledgeDocument document) {
        return new IngestionResponse(
                document.getDocumentNo(),
                document.getSourceType(),
                document.getSourceUrl(),
                document.getFileName(),
                document.getContentType(),
                document.getStatus(),
                document.getParser(),
                document.getTextCharCount(),
                document.getChunkCount(),
                document.getChunkStrategy(),
                document.getChunkSize(),
                document.getChunkOverlap(),
                document.getMaxChunks(),
                toInstant(document.getCreatedAt()),
                document.getErrorMessage()
        );
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 2.0));
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String conciseMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "服务未返回具体原因";
        }
        return truncate(message.replaceAll("\\s+", " ").trim(), 180);
    }

    // 判断失败是否适合交给 MQ 重试。
    private boolean isRetryableFailure(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getStatus().is5xxServerError();
        }
        return true;
    }

    // 清洗上传文件名。
    private String sanitizeFileName(String value, String fallback) {
        String fileName = value == null || value.isBlank() ? fallback : value.trim();
        fileName = fileName.replace("\\", "/");
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        fileName = fileName.replaceAll("[\\p{Cntrl}]", "");
        return fileName.isBlank() ? fallback : fileName;
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
