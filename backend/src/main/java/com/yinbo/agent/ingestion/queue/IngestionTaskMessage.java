package com.yinbo.agent.ingestion.queue;

import org.slf4j.MDC;

// 文档入库 RocketMQ 任务消息。
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

    // 创建文档分块任务消息。
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

    // 创建重建向量任务消息。
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

    // 获取可用于日志链路的 requestId。
    public String resolvedRequestId() {
        return requestId == null || requestId.isBlank() ? "-" : requestId;
    }

    // 从当前 MDC 读取 requestId。
    private static String currentRequestId() {
        String requestId = MDC.get(MDC_REQUEST_ID_KEY);
        return requestId == null || requestId.isBlank() ? null : requestId;
    }

    // 获取默认后的任务动作。
    public String resolvedAction() {
        return action == null || action.isBlank() ? ACTION_CHUNK : action;
    }
}
