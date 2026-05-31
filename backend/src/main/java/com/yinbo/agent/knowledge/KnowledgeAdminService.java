package com.yinbo.agent.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.ChunkingOptions;
import com.yinbo.agent.ingestion.entity.KnowledgeChunk;
import com.yinbo.agent.ingestion.entity.KnowledgeDocument;
import com.yinbo.agent.ingestion.mapper.KnowledgeChunkMapper;
import com.yinbo.agent.ingestion.mapper.KnowledgeDocumentMapper;
import com.yinbo.agent.ingestion.queue.IngestionTaskMessage;
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
import com.yinbo.agent.storage.ObjectStorageService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.ai.vectorstore.VectorStore;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
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
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FAILED = "FAILED";

    private final RagProperties ragProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final RocketMQTemplate rocketMQTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectStorageService objectStorageService;

    public KnowledgeAdminService(
            RagProperties ragProperties,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper,
            ObjectProvider<VectorStore> vectorStoreProvider,
            RocketMQTemplate rocketMQTemplate,
            JdbcTemplate jdbcTemplate,
            ObjectStorageService objectStorageService
    ) {
        this.ragProperties = ragProperties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.vectorStoreProvider = vectorStoreProvider;
        this.rocketMQTemplate = rocketMQTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectStorageService = objectStorageService;
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
            log.warn(
                    "event=knowledge_base_create_failed userId={} collectionName={} reason=duplicate_collection",
                    adminUser.getId(),
                    sanitizeLogValue(request.collectionName())
            );
            throw new BusinessException(HttpStatus.CONFLICT, "collection 名称已存在，请换一个");
        }
        log.info(
                "event=knowledge_base_created userId={} knowledgeBaseId={} collectionName={}",
                adminUser.getId(),
                knowledgeBase.getKnowledgeBaseNo(),
                sanitizeLogValue(knowledgeBase.getCollectionName())
        );
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
        log.info(
                "event=knowledge_base_updated knowledgeBaseId={} name={}",
                knowledgeBase.getKnowledgeBaseNo(),
                sanitizeLogValue(knowledgeBase.getName())
        );
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
        log.info(
                "event=knowledge_base_deleted knowledgeBaseId={} documentCount={}",
                knowledgeBase.getKnowledgeBaseNo(),
                documents.size()
        );
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

    public KnowledgeDocumentResponse rechunkDocument(String documentId, RechunkDocumentRequest request) {
        KnowledgeDocument document = requireDocument(documentId);
        requireKnowledgeBaseById(document.getKnowledgeBaseId());
        if (STATUS_PROCESSING.equals(document.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档正在处理中，请稍后再试");
        }
        ChunkingOptions options = ChunkingOptions.from(
                ragProperties,
                request == null ? null : request.strategy(),
                request == null ? null : request.chunkSize(),
                request == null ? null : request.chunkOverlap(),
                request == null ? null : request.maxChunks()
        );
        if (!hasProcessableSource(document)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文档缺少原始文件或可重新分块的文本内容");
        }

        document.setStatus(STATUS_PROCESSING);
        document.setErrorMessage(null);
        document.setChunkStrategy(options.strategy().name());
        document.setChunkSize(options.chunkSize());
        document.setChunkOverlap(options.chunkOverlap());
        document.setMaxChunks(options.maxChunks());
        knowledgeDocumentMapper.updateById(document);

        IngestionTaskMessage message = IngestionTaskMessage.chunk(
                document.getDocumentNo(),
                options.strategy().name(),
                options.chunkSize(),
                options.chunkOverlap(),
                options.maxChunks()
        );
        try {
            SendResult sendResult = rocketMQTemplate.syncSend(ragProperties.ingestionTopic(), message);
            log.info(
                    "event=mq_send topic={} action={} documentId={} sourceRequestId={} messageId={} sendStatus={} strategy={} chunkSize={} chunkOverlap={} maxChunks={}",
                    ragProperties.ingestionTopic(),
                    message.resolvedAction(),
                    message.documentId(),
                    message.resolvedRequestId(),
                    sendResult.getMsgId(),
                    sendResult.getSendStatus(),
                    options.strategy().name(),
                    options.chunkSize(),
                    options.chunkOverlap(),
                    options.maxChunks()
            );
        } catch (RuntimeException exception) {
            document.setStatus(STATUS_FAILED);
            document.setErrorMessage(truncate("分块任务投递失败，请检查 RocketMQ：" + conciseMessage(exception), 1000));
            knowledgeDocumentMapper.updateById(document);
            log.error(
                    "event=mq_send_failed topic={} action={} documentId={} sourceRequestId={} type={} message={}",
                    ragProperties.ingestionTopic(),
                    message.resolvedAction(),
                    message.documentId(),
                    message.resolvedRequestId(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "分块任务投递失败，请检查 RocketMQ");
        }
        return toDocumentResponse(document);
    }

    public KnowledgeDocumentResponse rebuildDocumentVectors(String documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        requireKnowledgeBaseById(document.getKnowledgeBaseId());
        if (STATUS_PROCESSING.equals(document.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档正在处理中，请稍后再试");
        }
        if (listChunkEntities(document.getId()).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文档没有可重建的分块");
        }

        document.setStatus(STATUS_PROCESSING);
        document.setErrorMessage(null);
        knowledgeDocumentMapper.updateById(document);

        IngestionTaskMessage message = IngestionTaskMessage.rebuildVectors(document.getDocumentNo());
        try {
            SendResult sendResult = rocketMQTemplate.syncSend(ragProperties.ingestionTopic(), message);
            log.info(
                    "event=mq_send topic={} action={} documentId={} sourceRequestId={} messageId={} sendStatus={}",
                    ragProperties.ingestionTopic(),
                    message.resolvedAction(),
                    message.documentId(),
                    message.resolvedRequestId(),
                    sendResult.getMsgId(),
                    sendResult.getSendStatus()
            );
        } catch (RuntimeException exception) {
            document.setStatus(STATUS_FAILED);
            document.setErrorMessage(truncate("重建向量任务投递失败，请检查 RocketMQ：" + conciseMessage(exception), 1000));
            knowledgeDocumentMapper.updateById(document);
            log.error(
                    "event=mq_send_failed topic={} action={} documentId={} sourceRequestId={} type={} message={}",
                    ragProperties.ingestionTopic(),
                    message.resolvedAction(),
                    message.documentId(),
                    message.resolvedRequestId(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "重建向量任务投递失败，请检查 RocketMQ");
        }
        return toDocumentResponse(document);
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
        deleteOriginalDocumentAfterCommit(document);
        log.info(
                "event=knowledge_document_deleted documentId={} chunkCount={} vectorDeleteRequested={}",
                document.getDocumentNo(),
                chunks.size(),
                vectorStore != null
        );
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

    private void deleteOriginalDocumentAfterCommit(KnowledgeDocument document) {
        if (document.getStorageObjectKey() == null || document.getStorageObjectKey().isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            objectStorageService.deleteQuietly(document.getStorageBucket(), document.getStorageObjectKey());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectStorageService.deleteQuietly(document.getStorageBucket(), document.getStorageObjectKey());
            }
        });
    }

    private boolean hasProcessableSource(KnowledgeDocument document) {
        return (document.getStorageObjectKey() != null && !document.getStorageObjectKey().isBlank())
                || (document.getTextContent() != null && !document.getTextContent().isBlank())
                || !listChunkEntities(document.getId()).isEmpty();
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

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 2.0));
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

    private Instant toInstantOrNull(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
