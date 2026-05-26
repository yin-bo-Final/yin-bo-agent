package com.yinbo.agent.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yinbo.agent.auth.entity.AuthUser;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.entity.KnowledgeChunk;
import com.yinbo.agent.ingestion.entity.KnowledgeDocument;
import com.yinbo.agent.ingestion.mapper.KnowledgeChunkMapper;
import com.yinbo.agent.ingestion.mapper.KnowledgeDocumentMapper;
import com.yinbo.agent.knowledge.dto.ChunkEnabledRequest;
import com.yinbo.agent.knowledge.dto.CreateKnowledgeBaseRequest;
import com.yinbo.agent.knowledge.dto.KnowledgeBaseResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeChunkResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeDocumentResponse;
import com.yinbo.agent.knowledge.dto.KnowledgeOverviewResponse;
import com.yinbo.agent.knowledge.entity.KnowledgeBase;
import com.yinbo.agent.knowledge.mapper.KnowledgeBaseMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeAdminService {

    private final RagProperties ragProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeAdminService(
            RagProperties ragProperties,
            KnowledgeBaseMapper knowledgeBaseMapper,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper,
            ObjectProvider<VectorStore> vectorStoreProvider,
            JdbcTemplate jdbcTemplate
    ) {
        this.ragProperties = ragProperties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.vectorStoreProvider = vectorStoreProvider;
        this.jdbcTemplate = jdbcTemplate;
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
    public KnowledgeChunkResponse updateChunkEnabled(String chunkId, ChunkEnabledRequest request) {
        KnowledgeChunk chunk = requireChunk(chunkId);
        chunk.setEnabled(request != null && request.enabledValue());
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
        if (vectorStore != null) {
            vectorStore.delete(chunk.getVectorDocumentId());
        }
        knowledgeChunkMapper.deleteById(chunk.getId());
        updateDocumentChunkCount(chunk.getDocumentId());
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

    private Instant toInstant(LocalDateTime value) {
        return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private Instant toInstantOrNull(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
