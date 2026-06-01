package com.yinbo.agent.ingestion.optimizer;

import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.model.ChunkingOptions;
import com.yinbo.agent.ingestion.model.ChunkingStrategy;
import com.yinbo.agent.ingestion.model.DocumentChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
// 文档切块优化器。
public class DocumentChunkOptimizer {

    private final RagProperties ragProperties;

    // 注入 RAG 配置。
    public DocumentChunkOptimizer(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    // 规范化、去重并合并过短分块。
    public List<DocumentChunk> optimize(List<DocumentChunk> chunks, ChunkingOptions options) {
        List<DocumentChunk> normalized = normalizeAndDedupe(chunks);
        List<DocumentChunk> merged = mergeShortChunks(normalized, options);
        if (options.strategy() == ChunkingStrategy.AUTO && merged.size() > options.maxChunks()) {
            merged = mergeAutoChunks(merged, options);
        }
        List<DocumentChunk> reindexed = new ArrayList<>();
        for (DocumentChunk chunk : merged) {
            reindexed.add(new DocumentChunk(reindexed.size(), chunk.title(), chunk.content()));
        }
        return reindexed;
    }

    // 规范化内容并去除重复分块。
    private List<DocumentChunk> normalizeAndDedupe(List<DocumentChunk> chunks) {
        List<DocumentChunk> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (DocumentChunk chunk : chunks) {
            String content = normalizeContent(chunk.content());
            if (content.isBlank()) {
                continue;
            }
            String fingerprint = content.replaceAll("\\s+", " ").trim();
            if (!seen.add(fingerprint)) {
                continue;
            }
            normalized.add(new DocumentChunk(chunk.index(), chunk.title(), content));
        }
        return normalized;
    }

    // 合并过短分块。
    private List<DocumentChunk> mergeShortChunks(List<DocumentChunk> chunks, ChunkingOptions options) {
        List<DocumentChunk> merged = new ArrayList<>();
        DocumentChunk pending = null;
        int softLimit = options.chunkSize() + options.chunkOverlap();

        for (DocumentChunk chunk : chunks) {
            if (pending == null) {
                pending = chunk;
                continue;
            }
            boolean pendingIsShort = pending.content().length() < ragProperties.minChunkSize();
            boolean canMerge = pending.content().length() + chunk.content().length() + 2 <= softLimit;
            if (pendingIsShort && canMerge) {
                pending = new DocumentChunk(
                        pending.index(),
                        firstNonBlank(pending.title(), chunk.title()),
                        pending.content() + "\n\n" + chunk.content()
                );
                continue;
            }
            merged.add(pending);
            pending = chunk;
        }
        if (pending != null) {
            merged.add(pending);
        }
        return merged;
    }

    // 自动策略下继续合并分块直到接近上限。
    private List<DocumentChunk> mergeAutoChunks(List<DocumentChunk> chunks, ChunkingOptions options) {
        List<DocumentChunk> current = chunks;
        int totalChars = chunks.stream()
                .mapToInt(chunk -> chunk.content().length() + 2)
                .sum();
        int targetSize = (int) Math.ceil(totalChars / Math.max(1.0, options.maxChunks() * 0.85));
        int softLimit = Math.max(options.chunkSize() + options.chunkOverlap(), targetSize);
        softLimit = Math.min(ChunkingOptions.MAX_EMBEDDING_CHUNK_CHARS, Math.max(softLimit, ragProperties.minChunkSize() * 4));

        while (current.size() > options.maxChunks() && softLimit <= ChunkingOptions.MAX_EMBEDDING_CHUNK_CHARS) {
            List<DocumentChunk> merged = mergeSequentially(current, softLimit);
            if (merged.size() >= current.size()) {
                softLimit = Math.min(ChunkingOptions.MAX_EMBEDDING_CHUNK_CHARS, softLimit * 2);
                if (softLimit == ChunkingOptions.MAX_EMBEDDING_CHUNK_CHARS) {
                    List<DocumentChunk> finalAttempt = mergeSequentially(current, softLimit);
                    return finalAttempt.size() < current.size() ? finalAttempt : current;
                }
                continue;
            }
            current = merged;
            if (current.size() <= options.maxChunks()) {
                return current;
            }
            softLimit = Math.min(ChunkingOptions.MAX_EMBEDDING_CHUNK_CHARS, softLimit * 2);
        }
        return current;
    }

    // 按顺序合并相邻分块。
    private List<DocumentChunk> mergeSequentially(List<DocumentChunk> chunks, int softLimit) {
        List<DocumentChunk> merged = new ArrayList<>();
        DocumentChunk pending = null;
        for (DocumentChunk chunk : chunks) {
            if (pending == null) {
                pending = chunk;
                continue;
            }
            int mergedLength = pending.content().length() + chunk.content().length() + 2;
            if (mergedLength <= softLimit) {
                pending = new DocumentChunk(
                        pending.index(),
                        firstNonBlank(pending.title(), chunk.title()),
                        pending.content() + "\n\n" + chunk.content()
                );
                continue;
            }
            merged.add(pending);
            pending = chunk;
        }
        if (pending != null) {
            merged.add(pending);
        }
        return merged;
    }

    // 规范化分块内容。
    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    // 返回第一个非空标题。
    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
