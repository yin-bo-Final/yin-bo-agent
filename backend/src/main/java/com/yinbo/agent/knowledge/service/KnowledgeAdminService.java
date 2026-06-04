package com.yinbo.agent.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.entity.KnowledgeChunk;
import com.yinbo.agent.ingestion.entity.KnowledgeDocument;
import com.yinbo.agent.ingestion.mapper.KnowledgeChunkMapper;
import com.yinbo.agent.ingestion.mapper.KnowledgeDocumentMapper;
import com.yinbo.agent.ingestion.model.ChunkingOptions;
import com.yinbo.agent.ingestion.service.IngestionTaskProducerService;
import com.yinbo.agent.ingestion.service.IngestionTaskService;
import com.yinbo.agent.ingestion.vector.PgVectorRepository;
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
import com.yinbo.agent.storage.service.ObjectStorageService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
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
// 管理后台知识库业务服务。
public class KnowledgeAdminService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeAdminService.class);
    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FAILED = "FAILED";

    private final RagProperties ragProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final PgVectorRepository pgVectorRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectStorageService objectStorageService;
    private final IngestionTaskService ingestionTaskService;
    private final IngestionTaskProducerService ingestionTaskProducerService;

    // 注入知识库、文档、分块、向量和 MQ 相关依赖。
    public KnowledgeAdminService(
            RagProperties ragProperties,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper,
            PgVectorRepository pgVectorRepository,
            JdbcTemplate jdbcTemplate,
            ObjectStorageService objectStorageService,
            IngestionTaskService ingestionTaskService,
            IngestionTaskProducerService ingestionTaskProducerService
    ) {
        this.ragProperties = ragProperties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.pgVectorRepository = pgVectorRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectStorageService = objectStorageService;
        this.ingestionTaskService = ingestionTaskService;
        this.ingestionTaskProducerService = ingestionTaskProducerService;
    }

    // 查询知识库概览统计。
    public KnowledgeOverviewResponse overview() {
        long knowledgeBaseCount = nullToZero(knowledgeBaseMapper.selectCount(new LambdaQueryWrapper<KnowledgeBase>()));
        long totalDocumentCount = nullToZero(knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()));
        long baseWithDocuments = queryLongOrZero(
                "SELECT COUNT(*) FROM (SELECT knowledge_base_id FROM knowledge_document WHERE knowledge_base_id IS NOT NULL GROUP BY knowledge_base_id) t"
        );
        return new KnowledgeOverviewResponse(
                knowledgeBaseCount,
                totalDocumentCount,
                baseWithDocuments,
                ragProperties.embeddingModel()
        );
    }

    @Transactional
    // 创建知识库。
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

    // 查询知识库列表。
    public List<KnowledgeBaseResponse> list() {
        return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                        .orderByDesc(KnowledgeBase::getCreatedAt))
                .stream()
                .map(this::toKnowledgeBaseResponse)
                .toList();
    }

    // 查询知识库详情。
    public KnowledgeBaseResponse detail(String knowledgeBaseId) {
        return toKnowledgeBaseResponse(requireKnowledgeBase(knowledgeBaseId));
    }

    @Transactional
    // 更新知识库基础信息。
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
    // 删除知识库及其文档和分块。
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

    // 根据业务编号获取知识库。
    public KnowledgeBase requireKnowledgeBase(String knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getKnowledgeBaseNo, knowledgeBaseId)
                .last("LIMIT 1"));
        if (knowledgeBase == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "知识库不存在");
        }
        return knowledgeBase;
    }

    // 查询知识库下的文档列表。
    public List<KnowledgeDocumentResponse> listDocuments(String knowledgeBaseId) {
        KnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        return knowledgeDocumentMapper.selectList(new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBase.getId())
                        .orderByDesc(KnowledgeDocument::getUpdatedAt))
                .stream()
                .filter(document -> !ingestionTaskService.hasDeadTaskForDocument(document.getId()))
                .map(this::toDocumentResponse)
                .toList();
    }

    // 查询文档详情。
    public KnowledgeDocumentResponse documentDetail(String documentId) {
        return toDocumentResponse(requireDocument(documentId));
    }

    // 查询文档分块列表。
    public List<KnowledgeChunkResponse> listChunks(String documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        return knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                        .eq(KnowledgeChunk::getDocumentId, document.getId())
                        .orderByAsc(KnowledgeChunk::getChunkIndex))
                .stream()
                .map(this::toChunkResponse)
                .toList();
    }

    // 投递文档重新分块任务。
    public KnowledgeDocumentResponse rechunkDocument(String documentId, RechunkDocumentRequest request) {
        KnowledgeDocument document = requireDocument(documentId);
        requireKnowledgeBaseById(document.getKnowledgeBaseId());
        if (isBusyDocument(document)) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档正在处理中，请稍后再试");
        }
        ingestionTaskService.requireDocumentNotDead(document.getId());
        if (STATUS_FAILED.equals(document.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档处理失败，请到失败任务页重试或删除文档");
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

        String taskId = ingestionTaskProducerService.sendChunkTransaction(document.getDocumentNo(), options);
        log.info(
                "event=knowledge_document_chunk_submitted documentId={} taskId={} strategy={} chunkSize={} chunkOverlap={} maxChunks={}",
                document.getDocumentNo(),
                taskId,
                options.strategy().name(),
                options.chunkSize(),
                options.chunkOverlap(),
                options.maxChunks()
        );
        return documentDetail(documentId);
    }

    // 投递文档向量重建任务。
    public KnowledgeDocumentResponse rebuildDocumentVectors(String documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        requireKnowledgeBaseById(document.getKnowledgeBaseId());
        if (isBusyDocument(document)) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档正在处理中，请稍后再试");
        }
        ingestionTaskService.requireDocumentNotDead(document.getId());
        if (STATUS_FAILED.equals(document.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档处理失败，请到失败任务页重试或删除文档");
        }
        if (listChunkEntities(document.getId()).isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "文档没有可重建的分块");
        }

        String taskId = ingestionTaskProducerService.sendRebuildVectorsTransaction(document.getDocumentNo());
        log.info(
                "event=knowledge_document_vector_rebuild_submitted documentId={} taskId={}",
                document.getDocumentNo(),
                taskId
        );
        return documentDetail(documentId);
    }

    @Transactional
    // 删除文档及其分块。
    public void deleteDocument(String documentId) {
        KnowledgeDocument document = requireDocument(documentId);
        requireDocumentNotBusy(document);
        deleteDocumentByEntity(document);
    }

    @Transactional
    // 更新单个分块启用状态。
    public KnowledgeChunkResponse updateChunkEnabled(String chunkId, ChunkEnabledRequest request) {
        KnowledgeChunk chunk = requireChunk(chunkId);
        requireMutableDocument(chunk.getDocumentId());
        chunk.setEnabled(request != null && request.enabledValue());
        knowledgeChunkMapper.updateById(chunk);
        return toChunkResponse(chunk);
    }

    @Transactional
    // 更新单个分块内容。
    public KnowledgeChunkResponse updateChunk(String chunkId, UpdateChunkRequest request) {
        KnowledgeChunk chunk = requireChunk(chunkId);
        requireMutableDocument(chunk.getDocumentId());
        String content = request.content().trim();
        chunk.setContent(content);
        chunk.setCharCount(content.length());
        chunk.setTokenCount(estimateTokenCount(content));
        knowledgeChunkMapper.updateById(chunk);
        return toChunkResponse(chunk);
    }

    @Transactional
    // 批量更新文档下所有分块的启用状态。
    public List<KnowledgeChunkResponse> updateDocumentChunksEnabled(String documentId, ChunkEnabledRequest request) {
        KnowledgeDocument document = requireDocument(documentId);
        requireDocumentNotBusy(document);
        boolean enabled = request != null && request.enabledValue();
        knowledgeChunkMapper.update(null, new LambdaUpdateWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, document.getId())
                .set(KnowledgeChunk::getEnabled, enabled));
        return listChunks(documentId);
    }

    @Transactional
    // 删除单个分块。
    public void deleteChunk(String chunkId) {
        KnowledgeChunk chunk = requireChunk(chunkId);
        requireMutableDocument(chunk.getDocumentId());
        pgVectorRepository.deleteByIds(vectorDocumentIds(List.of(chunk)));
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

    // 根据主键获取可修改分块的文档。
    private KnowledgeDocument requireMutableDocument(Long documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        requireDocumentNotBusy(document);
        return document;
    }

    private List<KnowledgeChunk> listChunkEntities(Long documentId) {
        return knowledgeChunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, documentId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
    }

    private void deleteDocumentByEntity(KnowledgeDocument document) {
        requireDocumentNotBusy(document);
        List<KnowledgeChunk> chunks = listChunkEntities(document.getId());
        pgVectorRepository.deleteByIds(vectorDocumentIds(chunks));
        knowledgeChunkMapper.delete(new LambdaQueryWrapper<KnowledgeChunk>()
                .eq(KnowledgeChunk::getDocumentId, document.getId()));
        knowledgeDocumentMapper.deleteById(document.getId());
        deleteOriginalDocumentAfterCommit(document);
        log.info(
                "event=knowledge_document_deleted documentId={} chunkCount={} vectorDeleteRequested={}",
                document.getDocumentNo(),
                chunks.size(),
                !chunks.isEmpty()
        );
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
            // 事务提交后删除原始文件。
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

    // 判断文档是否处于上传或处理中的忙碌状态。
    private boolean isBusyDocument(KnowledgeDocument document) {
        return document != null
                && (STATUS_UPLOADING.equals(document.getStatus()) || STATUS_PROCESSING.equals(document.getStatus()));
    }

    // 忙碌文档不允许删除或修改分块，避免和异步入库事务互相覆盖。
    private void requireDocumentNotBusy(KnowledgeDocument document) {
        if (isBusyDocument(document)) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档正在处理中，请稍后再试");
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

    // 查询必须返回数值的 JDBC 统计指标，空值统一按 0 处理。
    private long queryLongOrZero(String sql) {
        return nullToZero(jdbcTemplate.queryForObject(sql, Long.class));
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 2.0));
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
