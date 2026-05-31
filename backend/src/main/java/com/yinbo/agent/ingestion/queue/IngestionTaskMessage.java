package com.yinbo.agent.ingestion.queue;

import org.slf4j.MDC;

public record IngestionTaskMessage(
        String action,
        String documentId,
        String strategy,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxChunks,
        String requestId
) {
    public static final String ACTION_CHUNK = "CHUNK";
    public static final String ACTION_REBUILD_VECTORS = "REBUILD_VECTORS";
    private static final String MDC_REQUEST_ID_KEY = "requestId";

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
                maxChunks,
                currentRequestId()
        );
    }

    public static IngestionTaskMessage rebuildVectors(String documentId) {
        return new IngestionTaskMessage(
                ACTION_REBUILD_VECTORS,
                documentId,
                null,
                null,
                null,
                null,
                currentRequestId()
        );
    }

    public String resolvedRequestId() {
        return requestId == null || requestId.isBlank() ? "-" : requestId;
    }

    private static String currentRequestId() {
        String requestId = MDC.get(MDC_REQUEST_ID_KEY);
        return requestId == null || requestId.isBlank() ? null : requestId;
    }

    public String resolvedAction() {
        return action == null || action.isBlank() ? ACTION_CHUNK : action;
    }
}
