package com.yinbo.agent.ingestion.model;

// 文档入库执行结果。
public record IngestionExecutionResult(
        boolean success,
        boolean retryable,
        String message
) {

    // 创建成功结果。
    public static IngestionExecutionResult succeeded() {
        return new IngestionExecutionResult(true, false, null);
    }

    // 创建失败结果。
    public static IngestionExecutionResult failure(boolean retryable, String message) {
        return new IngestionExecutionResult(false, retryable, message);
    }
}
