package com.yinbo.agent.ingestion;

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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final RagProperties ragProperties;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final DocumentSourceReader documentSourceReader;
    private final TikaDocumentParser tikaDocumentParser;
    private final DocumentTextCleaner documentTextCleaner;
    private final RecursiveDocumentChunkSplitter chunkSplitter;
    private final DocumentChunkOptimizer chunkOptimizer;

    public DocumentIngestionService(
            RagProperties ragProperties,
            ObjectProvider<VectorStore> vectorStoreProvider,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper,
            DocumentSourceReader documentSourceReader,
            TikaDocumentParser tikaDocumentParser,
            DocumentTextCleaner documentTextCleaner,
            RecursiveDocumentChunkSplitter chunkSplitter,
            DocumentChunkOptimizer chunkOptimizer
    ) {
        this.ragProperties = ragProperties;
        this.vectorStoreProvider = vectorStoreProvider;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.documentSourceReader = documentSourceReader;
        this.tikaDocumentParser = tikaDocumentParser;
        this.documentTextCleaner = documentTextCleaner;
        this.chunkSplitter = chunkSplitter;
        this.chunkOptimizer = chunkOptimizer;
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
        return ingest(authUser, null, rawDocument, options);
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
        return ingest(authUser, knowledgeBase, rawDocument, options);
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
        return ingest(authUser, null, rawDocument, options);
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
        return ingest(authUser, knowledgeBase, rawDocument, options);
    }

    private IngestionResponse ingest(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            RawDocument rawDocument,
            ChunkingOptions options
    ) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "当前没有可用的向量存储，请检查 EmbeddingModel 和 PGVector 配置");
        }

        long totalStartedAt = System.nanoTime();
        KnowledgeDocument document = createProcessingDocument(authUser, knowledgeBase, rawDocument, options);
        List<String> vectorDocumentIds = new ArrayList<>();
        try {
            long parseStartedAt = System.nanoTime();
            ParsedDocument parsedDocument = tikaDocumentParser.parse(rawDocument);
            String cleanText = documentTextCleaner.clean(parsedDocument.text());
            if (cleanText.isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "清洗后没有可用文本");
            }
            long parseDurationMs = elapsedMillis(parseStartedAt);
            document.setTextExtractedAt(LocalDateTime.now());

            long chunkStartedAt = System.nanoTime();
            List<DocumentChunk> chunks = chunkSplitter.split(cleanText, parsedDocument.title(), options);
            chunks = chunkOptimizer.optimize(chunks, options);
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
            vectorStore.add(vectorDocuments);
            long embeddingDurationMs = elapsedMillis(embeddingStartedAt);
            chunkEntities.forEach(knowledgeChunkMapper::insert);

            document.setTextCharCount(cleanText.length());
            document.setChunkCount(chunks.size());
            document.setStatus(STATUS_COMPLETED);
            document.setErrorMessage(null);
            document.setParseDurationMs(parseDurationMs);
            document.setChunkDurationMs(chunkDurationMs);
            document.setEmbeddingDurationMs(embeddingDurationMs);
            long totalDurationMs = elapsedMillis(totalStartedAt);
            document.setTotalDurationMs(totalDurationMs);
            document.setOtherDurationMs(Math.max(0, totalDurationMs - parseDurationMs - chunkDurationMs - embeddingDurationMs));
            knowledgeDocumentMapper.updateById(document);
            return toResponse(document);
        } catch (RuntimeException exception) {
            rollbackVectorDocuments(vectorStore, vectorDocumentIds);
            markFailed(document, exception);
            throw exception;
        }
    }

    private KnowledgeDocument createProcessingDocument(
            AuthUser authUser,
            KnowledgeBase knowledgeBase,
            RawDocument rawDocument,
            ChunkingOptions options
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
        document.setTextCharCount(0);
        document.setChunkCount(0);
        document.setStatus(STATUS_PROCESSING);
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
            log.warn("Vector document rollback failed. ids={}", vectorDocumentIds, rollbackException);
        }
    }

    private void markFailed(KnowledgeDocument document, RuntimeException exception) {
        document.setStatus(STATUS_FAILED);
        document.setErrorMessage(truncate(exception.getMessage(), 1000));
        try {
            knowledgeDocumentMapper.updateById(document);
        } catch (RuntimeException updateException) {
            log.warn("Failed to update ingestion failure status. documentId={}", document.getDocumentNo(), updateException);
        }
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

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Instant toInstant(LocalDateTime value) {
        return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
