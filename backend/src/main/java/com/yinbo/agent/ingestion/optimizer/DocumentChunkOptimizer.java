package com.yinbo.agent.ingestion.optimizer;

import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.ChunkingOptions;
import com.yinbo.agent.ingestion.DocumentChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunkOptimizer {

    private final RagProperties ragProperties;

    public DocumentChunkOptimizer(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
    }

    public List<DocumentChunk> optimize(List<DocumentChunk> chunks, ChunkingOptions options) {
        List<DocumentChunk> normalized = normalizeAndDedupe(chunks);
        List<DocumentChunk> merged = mergeShortChunks(normalized, options);
        List<DocumentChunk> reindexed = new ArrayList<>();
        for (DocumentChunk chunk : merged) {
            reindexed.add(new DocumentChunk(reindexed.size(), chunk.title(), chunk.content()));
        }
        return reindexed;
    }

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

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
