package com.yinbo.agent.ingestion.queue;

import com.yinbo.agent.ingestion.model.ChunkingOptions;

// RocketMQ 事务消息本地事务参数。
public record IngestionTaskTransactionCommand(
        String type,
        String taskId,
        String documentId,
        String action,
        ChunkingOptions options,
        String requestId
) {
    public static final String TYPE_START = "START";
    public static final String TYPE_RETRY = "RETRY";
}
