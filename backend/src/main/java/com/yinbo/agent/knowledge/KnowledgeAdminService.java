package com.yinbo.agent.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.ChunkingOptions;
import com.yinbo.agent.ingestion.ChunkingStrategy;
import com.yinbo.agent.ingestion.DocumentChunk;
import com.yinbo.agent.ingestion.entity.KnowledgeChunk;
import com.yinbo.agent.ingestion.entity.KnowledgeDocument;
import com.yinbo.agent.ingestion.mapper.KnowledgeChunkMapper;
import com.yinbo.agent.ingestion.mapper.KnowledgeDocumentMapper;
import com.yinbo.agent.ingestion.optimizer.DocumentChunkOptimizer;
import com.yinbo.agent.ingestion.splitter.RecursiveDocumentChunkSplitter;
import com.yinbo.agent.knowledge.dto.ChunkEnabledRequest;
import com.yinbo.agent.knowledge.dto.CreateKnowledgeBaseRequest;
import com.yinbo.agent.knowledge.dto.KnowledgeBaseResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeChunkResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeDocumentResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeOverviewResponse;
import com.yinbo.agent.knowledge.dto.RechunkDocumentRequest;
import com.yinbo.agent.knowledge.dto.UpdateChunkRequest;
import com.yinbo.agent.knowledge.dto.UpdateKnowledgeBaseRequest;
import com.yinbo.agent.knowledge.entity.KnowledgeBase;
import com.yinbo.agent.knowledge.mapper.KnowledgeBaseMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeAdminService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAdminService.class);
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final RagProperties ragProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final JdbcTemplate jdbcTemplate;
    private final RecursiveDocumentChunkSplitter chunkSplitter;
    private final DocumentChunkOptimizer chunkOptimizer;

    public KnowledgeAdminService(
            RagProperties ragProperties,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper,
            ObjectProvider<VectorStore> vectorStoreProvider,
            JdbcTemplate jdbcTemplate,
            RecursiveDocumentChunkSplitter chunkSplitter,
            DocumentChunkOptimizer chunkOptimizer
    ) {
        this.ragProperties = ragProperties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.vectorStoreProvider = vectorStoreProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.chunkSplitter = chunkSplitter;
        this.chunkOptimizer = chunkOptimizer;
    }

    public KnowledgeOverviewResponse overview() {
        long knowledgeBaseCount = nullToZero(knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBase>()));
        long totalDocumentCount = nullToZero(knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()));
        long baseWithDocuments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (SELECT knowledge_base_id FROM knowledge_document WHERE knowledge_base_id IS NOT NULL GROUP BY knowledge_base_id) t",
                Long.class
        );
        return new KnowledgeOverviewResponse(
                knowledgeBaseCount,
                totalDocumentCount,
                baseWithDocuments,
                ragProperties.embeddingModel()
        );
    }

    @Transactional
    public KnowledgeBaseResponse create(AuthUser adminUser, CreateKnowledgeBaseRequest request) {
        String requestedEmbeddingModel = request.embeddingModel().trim();
        String configuredEmbeddingModel = ragProperties.embeddingModel();
        if (!configuredEmbeddingModel.equals(requestedEmbeddingModel)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "当前版本仅支持全局 Embedding 模型：" + configuredEmbeddingModel
            );
        }

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setKnowledgeBaseNo(UUID.randomUUID().toString());
        knowledgeBase.setName(request.name().trim());
        knowledgeBase.setEmbeddingModel(configuredEmbeddingModel);
        knowledgeBase.setCollectionName(request.collectionName().trim());
        knowledgeBase.setOwnerUserId(adminUser.getId());
        knowledgeBase.setStatus("ACTIVE");
        try {
            knowledgeBaseMapper.insert(knowledgeBase);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "collection 名称已存在，请换一个");
        }
        return toKnowledgeBaseResponse(knowledgeBase);
    }

    public List<KnowledgeBaseResponse> list() {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                        .orderByDesc(KnowledgeBase::getCreatedAt))
                .stream()
                .map(this::toKnowledgeBaseResponse)
                .toList();
    }

    public KnowledgeBaseResponse detail(String knowledgeBaseId) {
        return toKnowledgeBaseResponse(requireKnowledgeBase(knowledgeBaseId));
    }

    @Transactional
    public KnowledgeBaseResponse updateKnowledgeBase(String knowledgeBaseId, UpdateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        knowledgeBase.setName(request.name().trim());
        knowledgeBaseMapper.updateById(knowledgeBase);
        return toKnowledgeBaseResponse(knowledgeBase);
    }

    @Transactional
    public void deleteKnowledgeBase(String knowledgeBaseId) {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBase.getId()));
        for (KnowledgeDocument document : documents) {
            deleteDocumentByEntity(document);
        }
        knowledgeBaseMapper.deleteById(knowledgeBase.getId());
    }

    public KnowledgeBase requireKnowledgeBase(String knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getKnowledgeBaseNo, knowledgeBaseId)
                .last("LIMIT 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "知识库不存在");
        }
        return knowledgeBase;
    }

    public List<KnowledgeDocumentResponse> listDocuments(String knowledgeBaseId) {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        return knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBase.getId())
                        .orderByDesc(KnowledgeDocument::getUpdatedAt))
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    public KnowledgeDocumentResponse documentDetail(String documentId) {
        return toDocumentResponse(requireDocument(documentId));
    }

    public List<KnowledgeChunkResponse> listChunks(String documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        return knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getDocumentId, document.getId())
                        .orderByAsc(KnowledgeChunk::getChunkIndex))
                .stream()
                .map(this::toChunkResponse)
                .toList();
    }

    @Transactional
    public KnowledgeDocumentResponse rechunkDocument(String documentId, RechunkDocumentRequest request) {
        KnowledgeDocument document = requireDocument(documentId);
        KnowledgeBase knowledgeBase = requireKnowledgeBaseById(document.getKnowledgeBaseId());
        VectorStore vectorStore = requireVectorStore();
        ChunkingOptions options = ChunkingOptions.from(
                ragProperties,
                request == null ? null : request.strategy(),
                request == null ? null : request.chunkSize(),
                request == null ? null : request.chunkOverlap(),
                request == null ? null : request.maxChunks()
        );

        String text = resolveDocumentText(document);
        options = options.adaptForTextLength(text.length());
        long totalStartedAt = System.nanoTime();
        List<KnowledgeChunk> oldChunks = listChunkEntities(document.getId());
        List<String> newVectorDocumentIds = new ArrayList<>();
        try {
            long chunkStartedAt = System.nanoTime();
            List<DocumentChunk> chunks = chunkSplitter.split(text, documentTitle(document), options);
            chunks = chunkOptimizer.optimize(chunks, options);
            validateChunksForEmbedding(chunks, options);
            if (chunks.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "文档没有生成可入库的切块");
            }
            if (chunks.size() > options.maxChunks()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "优化后切块数量超过上限，请调大 chunkSize 或 maxChunks");
            }
            long chunkDurationMs = elapsedMillis(chunkStartedAt);

            long embeddingStartedAt = System.nanoTime();
            List<Document> vectorDocuments = new ArrayList<>();
            List<KnowledgeChunk> chunkEntities = new ArrayList<>();
            for (DocumentChunk chunk : chunks) {
                String vectorDocumentId = UUID.randomUUID().toString();
                newVectorDocumentIds.add(vectorDocumentId);
                KnowledgeChunk chunkEntity = toChunkEntity(document, chunk, vectorDocumentId);
                chunkEntities.add(chunkEntity);
                vectorDocuments.add(toVectorDocument(document, knowledgeBase, chunkEntity));
            }
            addVectorDocuments(vectorStore, vectorDocuments);
            long embeddingDurationMs = elapsedMillis(embeddingStartedAt);
            knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                    .eq(KnowledgeChunk::getDocumentId, document.getId()));
            chunkEntities.forEach(knowledgeChunkMapper::insert);

            document.setStatus(STATUS_COMPLETED);
            document.setErrorMessage(null);
            document.setTextContent(text);
            document.setTextCharCount(text.length());
            document.setChunkCount(chunks.size());
            document.setChunkStrategy(options.strategy().name());
            document.setChunkSize(options.chunkSize());
            document.setChunkOverlap(options.chunkOverlap());
            document.setMaxChunks(options.maxChunks());
            document.setChunkDurationMs(chunkDurationMs);
            document.setEmbeddingDurationMs(embeddingDurationMs);
            long totalDurationMs = elapsedMillis(totalStartedAt);
            document.setTotalDurationMs(totalDurationMs);
            document.setOtherDurationMs(Math.max(0, totalDurationMs - chunkDurationMs - embeddingDurationMs));
            knowledgeDocumentMapper.updateById(document);
            deleteVectorDocumentsAfterCommit(vectorStore, oldChunks);
            return toDocumentResponse(document);
        } catch (RuntimeException exception) {
            safeDeleteVectorDocumentsByIds(vectorStore, newVectorDocumentIds);
            document.setStatus(STATUS_FAILED);
            document.setErrorMessage(truncate(exception.getMessage(), 1000));
            knowledgeDocumentMapper.updateById(document);
            throw exception;
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

    @Transactional
    public KnowledgeDocumentResponse rebuildDocumentVectors(String documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        KnowledgeBase knowledgeBase = requireKnowledgeBaseById(document.getKnowledgeBaseId());
        VectorStore vectorStore = requireVectorStore();
        List<KnowledgeChunk> chunks = listChunkEntities(document.getId());
        if (chunks.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文档没有可重建的分块");
        }

        long startedAt = System.nanoTime();
        List<String> oldVectorDocumentIds = vectorDocumentIds(chunks);
        List<String> newVectorDocumentIds = new ArrayList<>();
        try {
            List<Document> vectorDocuments = new ArrayList<>();
            for (KnowledgeChunk chunk : chunks) {
                String nextVectorDocumentId = UUID.randomUUID().toString();
                newVectorDocumentIds.add(nextVectorDocumentId);
                chunk.setVectorDocumentId(nextVectorDocumentId);
                vectorDocuments.add(toVectorDocument(document, knowledgeBase, chunk));
            }
            addVectorDocuments(vectorStore, vectorDocuments);
            chunks.forEach(knowledgeChunkMapper::updateById);

            document.setStatus(STATUS_COMPLETED);
            document.setErrorMessage(null);
            long embeddingDurationMs = elapsedMillis(startedAt);
            document.setEmbeddingDurationMs(embeddingDurationMs);
            refreshDurationSummary(document);
            knowledgeDocumentMapper.updateById(document);
            deleteVectorDocumentsByIdsAfterCommit(vectorStore, oldVectorDocumentIds);
            return toDocumentResponse(document);
        } catch (RuntimeException exception) {
            safeDeleteVectorDocumentsByIds(vectorStore, newVectorDocumentIds);
            throw exception;
        }
    }

    @Transactional
    public void deleteDocument(String documentId) {
        deleteDocumentByEntity(requireDocument(documentId));
    }

    @Transactional
    public KnowledgeChunkResponse updateChunkEnabled(String chunkId, ChunkEnabledRequest request) {
        KnowledgeChunk chunk = requireChunk(chunkId);
        chunk.setEnabled(request != null && request.enabledValue());
        knowledgeChunkMapper.updateById(chunk);
        return toChunkResponse(chunk);
    }

    @Transactional
    public KnowledgeChunkResponse updateChunk(String chunkId, UpdateChunkRequest request) {
        KnowledgeChunk chunk = requireChunk(chunkId);
        String content = request.content().trim();
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunk.setTokenCount(estimateTokenCount(content));
        knowledgeChunkMapper.updateById(chunk);
        return toChunkResponse(chunk);
    }

    @Transactional
    public List<KnowledgeChunkResponse> updateDocumentChunksEnabled(String documentId, ChunkEnabledRequest request) {
        KnowledgeDocument document = requireDocument(documentId);
        boolean enabled = request != null && request.enabledValue();
        knowledgeChunkMapper.update(null, new LambdaUpdateWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, document.getId())
                .set(KnowledgeChunk::getEnabled, enabled));
        return listChunks(documentId);
    }

    @Transactional
    public void deleteChunk(String chunkId) {
        KnowledgeChunk chunk = requireChunk(chunkId);
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null && chunk.getVectorDocumentId() != null && !chunk.getVectorDocumentId().isBlank()) {
            vectorStore.delete(List.of(chunk.getVectorDocumentId()));
        }
        knowledgeChunkMapper.deleteById(chunk.getId());
        updateDocumentChunkCount(chunk.getDocumentId());
    }

    private KnowledgeBase requireKnowledgeBaseById(Long knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文档未绑定知识库");
        }
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "知识库不存在");
        }
        return knowledgeBase;
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

    private KnowledgeChunk requireChunk(String chunkId) {
        KnowledgeChunk chunk = knowledgeChunkMapper.selectOne(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getChunkNo, chunkId)
                .last("LIMIT 1"));
        if (chunk == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分块不存在");
        }
        return chunk;
    }

    private VectorStore requireVectorStore() {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "当前没有可用的向量存储，请检查 EmbeddingModel 和 PGVector 配置");
        }
        return vectorStore;
    }

    private List<KnowledgeChunk> listChunkEntities(Long documentId) {
        return knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
    }

    private void deleteDocumentByEntity(KnowledgeDocument document) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        List<KnowledgeChunk> chunks = listChunkEntities(document.getId());
        if (vectorStore != null) {
            deleteVectorDocuments(vectorStore, chunks);
        }
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, document.getId()));
        knowledgeDocumentMapper.deleteById(document.getId());
    }

    private void deleteVectorDocuments(VectorStore vectorStore, List<KnowledgeChunk> chunks) {
        deleteVectorDocumentsByIds(vectorStore, vectorDocumentIds(chunks));
    }

    private void deleteVectorDocumentsByIds(VectorStore vectorStore, List<String> vectorIds) {
        if (vectorStore == null || vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        vectorStore.delete(vectorIds);
    }

    private void safeDeleteVectorDocumentsByIds(VectorStore vectorStore, List<String> vectorIds) {
        try {
            deleteVectorDocumentsByIds(vectorStore, vectorIds);
        } catch (RuntimeException exception) {
            log.warn("Vector document cleanup failed. ids={}", vectorIds, exception);
        }
    }

    private List<String> vectorDocumentIds(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<String> vectorIds = chunks.stream()
                .map(KnowledgeChunk::getVectorDocumentId)
                .filter(value -> value != null && !value.isBlank())
                .toList();
        return vectorIds;
    }

    private void deleteVectorDocumentsAfterCommit(VectorStore vectorStore, List<KnowledgeChunk> chunks) {
        deleteVectorDocumentsByIdsAfterCommit(vectorStore, vectorDocumentIds(chunks));
    }

    private void deleteVectorDocumentsByIdsAfterCommit(VectorStore vectorStore, List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteVectorDocumentsByIds(vectorStore, vectorIds);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeDeleteVectorDocumentsByIds(vectorStore, vectorIds);
            }
        });
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

    private String resolveDocumentText(KnowledgeDocument document) {
        if (document.getTextContent() != null && !document.getTextContent().isBlank()) {
            return document.getTextContent();
        }
        String text = String.join("\n\n", listChunkEntities(document.getId()).stream()
                .map(KnowledgeChunk::getContent)
                .filter(value -> value != null && !value.isBlank())
                .toList());
        if (text.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文档缺少可重新分块的文本内容");
        }
        return text;
    }

    private String documentTitle(KnowledgeDocument document) {
        String fileName = document.getFileName();
        if (fileName == null || fileName.isBlank()) {
            return "未命名文档";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
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

    private Document toVectorDocument(KnowledgeDocument document, KnowledgeBase knowledgeBase, KnowledgeChunk chunk) {
        return Document.builder()
                .id(chunk.getVectorDocumentId())
                .text(chunk.getContent())
                .metadata(toVectorMetadata(document, knowledgeBase, chunk))
                .build();
    }

    private Map<String, Object> toVectorMetadata(KnowledgeDocument document, KnowledgeBase knowledgeBase, KnowledgeChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putMetadata(metadata, "document_id", document.getDocumentNo());
        putMetadata(metadata, "document_pk", document.getId());
        putMetadata(metadata, "knowledge_base_id", knowledgeBase.getKnowledgeBaseNo());
        putMetadata(metadata, "knowledge_base_pk", knowledgeBase.getId());
        putMetadata(metadata, "collection_name", knowledgeBase.getCollectionName());
        putMetadata(metadata, "user_id", document.getUserId());
        putMetadata(metadata, "source_type", document.getSourceType());
        putMetadata(metadata, "source_url", document.getSourceUrl());
        putMetadata(metadata, "file_name", document.getFileName());
        putMetadata(metadata, "content_type", document.getContentType());
        putMetadata(metadata, "parser", document.getParser());
        putMetadata(metadata, "embedding_model", ragProperties.embeddingModel());
        putMetadata(metadata, "title", chunk.getTitle());
        putMetadata(metadata, "chunk_index", chunk.getChunkIndex());
        putMetadata(metadata, "ingested_at", Instant.now().toString());
        return metadata;
    }

    private void putMetadata(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private void updateDocumentChunkCount(Long documentId) {
        Long chunkCount = knowledgeChunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId));
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(documentId);
        if (document != null) {
            document.setChunkCount(chunkCount == null ? 0 : chunkCount.intValue());
            knowledgeDocumentMapper.updateById(document);
        }
    }

    private KnowledgeBaseResponse toKnowledgeBaseResponse(KnowledgeBase knowledgeBase) {
        Long documentCount = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBase.getId()));
        Long chunkCount = knowledgeChunkMapper.selectCount(new LambdaQueryWrapper<KnowledgeChunk>()
                .inSql(KnowledgeChunk::getDocumentId,
                        "SELECT id FROM knowledge_document WHERE knowledge_base_id = " + knowledgeBase.getId()));
        return new KnowledgeBaseResponse(
                knowledgeBase.getKnowledgeBaseNo(),
                knowledgeBase.getName(),
                knowledgeBase.getEmbeddingModel(),
                knowledgeBase.getCollectionName(),
                knowledgeBase.getStatus(),
                nullToZero(documentCount),
                nullToZero(chunkCount),
                toInstant(knowledgeBase.getCreatedAt()),
                toInstant(knowledgeBase.getUpdatedAt())
        );
    }

    private KnowledgeDocumentResponse toDocumentResponse(KnowledgeDocument document) {
        return new KnowledgeDocumentResponse(
                document.getDocumentNo(),
                document.getFileName(),
                document.getSourceType(),
                document.getSourceUrl(),
                document.getContentType(),
                document.getOriginalSizeBytes(),
                document.getStatus(),
                document.getTextCharCount(),
                document.getChunkCount(),
                document.getChunkStrategy(),
                document.getChunkSize(),
                document.getChunkOverlap(),
                document.getMaxChunks(),
                toInstantOrNull(document.getTextExtractedAt()),
                document.getParseDurationMs(),
                document.getChunkDurationMs(),
                document.getEmbeddingDurationMs(),
                document.getOtherDurationMs(),
                document.getTotalDurationMs(),
                toInstant(document.getCreatedAt()),
                toInstant(document.getUpdatedAt()),
                document.getErrorMessage()
        );
    }

    private KnowledgeChunkResponse toChunkResponse(KnowledgeChunk chunk) {
        return new KnowledgeChunkResponse(
                chunk.getChunkNo(),
                chunk.getChunkIndex(),
                chunk.getTitle(),
                chunk.getContent(),
                chunk.getEnabled(),
                chunk.getTokenCount(),
                chunk.getCharCount(),
                toInstant(chunk.getUpdatedAt())
        );
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private void refreshDurationSummary(KnowledgeDocument document) {
        long parseDurationMs = nullToZero(document.getParseDurationMs());
        long chunkDurationMs = nullToZero(document.getChunkDurationMs());
        long embeddingDurationMs = nullToZero(document.getEmbeddingDurationMs());
        long otherDurationMs = Math.max(0L, nullToZero(document.getOtherDurationMs()));
        document.setOtherDurationMs(otherDurationMs);
        document.setTotalDurationMs(parseDurationMs + chunkDurationMs + embeddingDurationMs + otherDurationMs);
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 2.0));
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

    private Instant toInstant(LocalDateTime value) {
        return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private Instant toInstantOrNull(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
