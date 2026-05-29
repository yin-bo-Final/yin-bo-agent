package com.yinbo.agent.ingestion.queue;

public record IngestionTaskMessage(
        String action,
        String documentId,
        String strategy,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxChunks
) {
    public static final String ACTION_CHUNK = "CHUNK";
    public static final String ACTION_REBUILD_VECTORS = "REBUILD_VECTORS";

    public static IngestionTaskMessage chunk(
            String documentId,
            String strategy,
            Integer chunkSize,
            Integer chunkOverlap,
            Integer maxChunks
    ) {
        return new IngestionTaskMessage(
                ACTION_CHUNK,
                documentId,
                strategy,
                chunkSize,
                chunkOverlap,
                maxChunks
        );
    }

    public static IngestionTaskMessage rebuildVectors(String documentId) {
        return new IngestionTaskMessage(
                ACTION_REBUILD_VECTORS,
                documentId,
                null,
                null,
                null,
                null
        );
    }

    public String resolvedAction() {
        return action == null || action.isBlank() ? ACTION_CHUNK : action;
    }
}
