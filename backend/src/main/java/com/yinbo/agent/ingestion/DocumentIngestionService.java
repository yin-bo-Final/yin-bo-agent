package com.yinbo.agent.ingestion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
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
import com.yinbo.agent.knowledge.entity.KnowledgeBase;
import com.yinbo.agent.knowledge.mapper.KnowledgeBaseMapper;
import com.yinbo.agent.storage.ObjectStorageService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final RagProperties ragProperties;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
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

    public DocumentIngestionService(
            RagProperties ragProperties,
            ObjectProvider<VectorStore> vectorStoreProvider,
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
        this.vectorStoreProvider = vectorStoreProvider;
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

    public IngestionResponse ingestUpload(
            AuthUser authUser,
            MultipartFile file,
            String strategy,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer maxChunks
    ) {
        RawDocument rawDocument = documentSourceReader.fromUpload(file);
        ChunkingOptions options = ChunkingOptions.from(ragProperties, strategy, chunkSize, chunkOverlap, maxChunks);
        return createUploadedDocument(authUser, null, rawDocument, options);
    }

    public IngestionResponse ingestUpload(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            MultipartFile file,
            String strategy,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer maxChunks
    ) {
        RawDocument rawDocument = documentSourceReader.fromUpload(file);
        ChunkingOptions options = ChunkingOptions.from(ragProperties, strategy, chunkSize, chunkOverlap, maxChunks);
        return createUploadedDocument(authUser, knowledgeBase, rawDocument, options);
    }

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

    @Transactional
    public void processDocument(String documentId, ChunkingOptions options) {
        KnowledgeDocument document = requireDocument(documentId);
        KnowledgeBase knowledgeBase = document.getKnowledgeBaseId() == null
                ? null
                : requireKnowledgeBaseById(document.getKnowledgeBaseId());
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.warn("event=ingestion_failed action=CHUNK documentId={} reason=vector_store_unavailable", documentId);
            markFailed(document, new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "当前没有可用的向量存储，请检查 EmbeddingModel 和 PGVector 配置"));
            return;
        }

        long totalStartedAt = System.nanoTime();
        List<String> vectorDocumentIds = new ArrayList<>();
        List<KnowledgeChunk> oldChunks = listChunkEntities(document.getId());
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

            List<Document> vectorDocuments = new ArrayList<>();
            List<KnowledgeChunk> chunkEntities = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                String vectorDocumentId = UUID.randomUUID().toString();
                vectorDocumentIds.add(vectorDocumentId);
                vectorDocuments.add(toVectorDocument(document, knowledgeBase, rawDocument, parsedDocument, chunk, vectorDocumentId));
                chunkEntities.add(toChunkEntity(document, chunk, vectorDocumentId));
            }
            long chunkDurationMs = elapsedMillis(chunkStartedAt);

            long embeddingStartedAt = System.nanoTime();
            addVectorDocuments(vectorStore, vectorDocuments);
            long embeddingDurationMs = elapsedMillis(embeddingStartedAt);
            chunkEntities.forEach(knowledgeChunkMapper::insert);

            document.setTextCharCount(cleanText.length());
            document.setTextContent(cleanText);
            document.setChunkCount(chunks.size());
            document.setStatus(STATUS_COMPLETED);
            document.setErrorMessage(null);
            document.setChunkStrategy(options.strategy().name());
            document.setChunkSize(options.chunkSize());
            document.setChunkOverlap(options.chunkOverlap());
            document.setMaxChunks(options.maxChunks());
            document.setParseDurationMs(parseDurationMs);
            document.setChunkDurationMs(chunkDurationMs);
            document.setEmbeddingDurationMs(embeddingDurationMs);
            long totalDurationMs = elapsedMillis(totalStartedAt);
            document.setTotalDurationMs(totalDurationMs);
            document.setOtherDurationMs(Math.max(0, totalDurationMs - parseDurationMs - chunkDurationMs - embeddingDurationMs));
            knowledgeDocumentMapper.updateById(document);
            deleteChunkEntities(oldChunks);
            deleteVectorDocumentsAfterCommit(vectorStore, oldChunks);
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
        } catch (RuntimeException exception) {
            rollbackVectorDocuments(vectorStore, vectorDocumentIds);
            deleteChunkEntitiesByVectorIds(vectorDocumentIds);
            markFailed(document, exception);
            log.warn(
                    "event=ingestion_failed action=CHUNK documentId={} type={} message={}",
                    document.getDocumentNo(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
        }
    }

    public void rebuildDocumentVectors(String documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        KnowledgeBase knowledgeBase = document.getKnowledgeBaseId() == null
                ? null
                : requireKnowledgeBaseById(document.getKnowledgeBaseId());
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.warn("event=ingestion_failed action=REBUILD_VECTORS documentId={} reason=vector_store_unavailable", documentId);
            markFailed(document, new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "当前没有可用的向量存储，请检查 EmbeddingModel 和 PGVector 配置"));
            return;
        }

        List<KnowledgeChunk> chunks = listChunkEntities(document.getId());
        if (chunks.isEmpty()) {
            log.warn("event=ingestion_failed action=REBUILD_VECTORS documentId={} reason=no_chunks", documentId);
            markFailed(document, new BusinessException(HttpStatus.BAD_REQUEST, "文档没有可重建的分块"));
            return;
        }

        List<String> oldVectorDocumentIds = vectorDocumentIds(chunks);
        List<String> newVectorDocumentIds = new ArrayList<>();
        try {
            log.info(
                    "event=ingestion_started action=REBUILD_VECTORS documentId={} chunkCount={}",
                    document.getDocumentNo(),
                    chunks.size()
            );
            transactionTemplate.executeWithoutResult(status -> {
                document.setStatus(STATUS_PROCESSING);
                document.setErrorMessage(null);
                knowledgeDocumentMapper.updateById(document);

                RawDocument rawDocument = toRawDocument(document);
                ParsedDocument parsedDocument = new ParsedDocument(
                        document.getTextContent() == null ? "" : document.getTextContent(),
                        documentTitle(document),
                        Map.of()
                );
                List<Document> vectorDocuments = new ArrayList<>();
                for (KnowledgeChunk chunk : chunks) {
                    String vectorDocumentId = UUID.randomUUID().toString();
                    newVectorDocumentIds.add(vectorDocumentId);
                    chunk.setVectorDocumentId(vectorDocumentId);
                    DocumentChunk documentChunk = new DocumentChunk(
                            chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex(),
                            chunk.getTitle(),
                            chunk.getContent()
                    );
                    vectorDocuments.add(toVectorDocument(document, knowledgeBase, rawDocument, parsedDocument, documentChunk, vectorDocumentId));
                }

                long embeddingStartedAt = System.nanoTime();
                addVectorDocuments(vectorStore, vectorDocuments);
                chunks.forEach(knowledgeChunkMapper::updateById);

                document.setStatus(STATUS_COMPLETED);
                document.setErrorMessage(null);
                document.setEmbeddingDurationMs(elapsedMillis(embeddingStartedAt));
                refreshDurationSummary(document);
                knowledgeDocumentMapper.updateById(document);
            });
            deleteVectorDocumentsByIdsAfterCommit(vectorStore, oldVectorDocumentIds);
            log.info(
                    "event=ingestion_completed action=REBUILD_VECTORS documentId={} chunkCount={} embeddingMs={} totalMs={}",
                    document.getDocumentNo(),
                    chunks.size(),
                    document.getEmbeddingDurationMs(),
                    document.getTotalDurationMs()
            );
        } catch (RuntimeException exception) {
            rollbackVectorDocuments(vectorStore, newVectorDocumentIds);
            markFailed(document, exception);
            log.warn(
                    "event=ingestion_failed action=REBUILD_VECTORS documentId={} type={} message={}",
                    document.getDocumentNo(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
        }
    }

    private void addVectorDocuments(VectorStore vectorStore, List<Document> vectorDocuments) {
        try {
            vectorStore.add(vectorDocuments);
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_GATEWAY,
                    "向量化失败，请检查 Embedding 服务或切块大小：" + conciseMessage(exception)
            );
        }
    }

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

    private KnowledgeDocument requireDocument(String documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDocumentNo, documentId)
                .last("LIMIT 1"));
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return document;
    }

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

    private void deleteChunkEntitiesByVectorIds(List<String> vectorDocumentIds) {
        if (vectorDocumentIds == null || vectorDocumentIds.isEmpty()) {
            return;
        }
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .in(KnowledgeChunk::getVectorDocumentId, vectorDocumentIds));
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

    private void deleteVectorDocumentsAfterCommit(VectorStore vectorStore, List<KnowledgeChunk> chunks) {
        deleteVectorDocumentsByIdsAfterCommit(vectorStore, vectorDocumentIds(chunks));
    }

    private void deleteVectorDocumentsByIdsAfterCommit(VectorStore vectorStore, List<String> vectorDocumentIds) {
        if (vectorDocumentIds == null || vectorDocumentIds.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            rollbackVectorDocuments(vectorStore, vectorDocumentIds);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rollbackVectorDocuments(vectorStore, vectorDocumentIds);
            }
        });
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

    private Document toVectorDocument(
            KnowledgeDocument document,
            KnowledgeBase knowledgeBase,
            RawDocument rawDocument,
            ParsedDocument parsedDocument,
            DocumentChunk chunk,
            String vectorDocumentId
    ) {
        return Document.builder()
                .id(vectorDocumentId)
                .text(chunk.content())
                .metadata(toVectorMetadata(document, knowledgeBase, rawDocument, parsedDocument, chunk))
                .build();
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

    private void rollbackVectorDocuments(VectorStore vectorStore, List<String> vectorDocumentIds) {
        if (vectorDocumentIds.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(vectorDocumentIds);
        } catch (RuntimeException rollbackException) {
            log.warn(
                    "event=vector_rollback_failed vectorCount={} type={} message={}",
                    vectorDocumentIds.size(),
                    rollbackException.getClass().getSimpleName(),
                    sanitizeLogValue(rollbackException.getMessage()),
                    rollbackException
            );
        }
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
