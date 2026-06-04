package com.yinbo.agent.ingestion.queue;

import com.yinbo.agent.common.service.RedisSemaphoreService;
import com.yinbo.agent.config.ConcurrencyLimitProperties;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.entity.IngestionTask;
import com.yinbo.agent.ingestion.model.ChunkingOptions;
import com.yinbo.agent.ingestion.model.IngestionExecutionResult;
import com.yinbo.agent.ingestion.service.DocumentIngestionService;
import com.yinbo.agent.ingestion.service.IngestionTaskService;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "${app.ai.rag.ingestion-topic:rag-ingestion-task}",
        consumerGroup = "${app.ai.rag.ingestion-consumer-group:yinbo-agent-ingestion-consumer}",
        maxReconsumeTimes = IngestionTaskService.DEFAULT_MAX_RETRIES
)
// 文档入库 MQ 消费者。
public class DocumentIngestionTaskConsumer implements RocketMQListener<IngestionTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionTaskConsumer.class);
    private static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final String INGESTION_SEMAPHORE_NAME = "ingestion:process:global";

    private final RagProperties ragProperties;
    private final ConcurrencyLimitProperties concurrencyLimitProperties;
    private final RedisSemaphoreService redisSemaphoreService;
    private final DocumentIngestionService documentIngestionService;
    private final IngestionTaskService ingestionTaskService;

    // 注入 RAG 配置、并发控制和文档入库服务。
    public DocumentIngestionTaskConsumer(
            RagProperties ragProperties,
            ConcurrencyLimitProperties concurrencyLimitProperties,
            RedisSemaphoreService redisSemaphoreService,
            DocumentIngestionService documentIngestionService,
            IngestionTaskService ingestionTaskService
    ) {
        this.ragProperties = ragProperties;
        this.concurrencyLimitProperties = concurrencyLimitProperties;
        this.redisSemaphoreService = redisSemaphoreService;
        this.documentIngestionService = documentIngestionService;
        this.ingestionTaskService = ingestionTaskService;
    }

    @Override
    // 消费文档入库任务消息。
    public void onMessage(IngestionTaskMessage message) {
        long startedAt = System.nanoTime();
        String action = message.resolvedAction();
        boolean requestIdBound = bindRequestId(message);
        log.info(
                "event=mq_consume_started topic={} action={} documentId={} sourceRequestId={}",
                ragProperties.ingestionTopic(),
                action,
                message.documentId(),
                message.resolvedRequestId()
        );
        try {
            if (!IngestionTaskMessage.ACTION_REBUILD_VECTORS.equals(action)
                    && !IngestionTaskMessage.ACTION_CHUNK.equals(action)) {
                throw new IllegalArgumentException("不支持的文档处理任务：" + action);
            }
            consumeWithIngestionPermit(message, action);
        } catch (RuntimeException exception) {
            log.error(
                    "event=mq_consume_failed topic={} action={} documentId={} costMs={} type={} message={}",
                    ragProperties.ingestionTopic(),
                    action,
                    message.documentId(),
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            throw exception;
        } finally {
            if (requestIdBound) {
                MDC.remove(MDC_REQUEST_ID_KEY);
            }
        }
    }

    // 在 Redis 信号量保护下执行入库任务。
    private void consumeWithIngestionPermit(IngestionTaskMessage message, String action) {
        ConcurrencyLimitProperties.Limit limit = concurrencyLimitProperties.ingestion();
        long startedAt = System.nanoTime();
        try (RedisSemaphoreService.Permit permit = acquireIngestionPermit(message, action, limit)) {
            IngestionTaskService.RunningClaim runningClaim = claimTaskRunningIfPresent(message);
            IngestionTask task = runningClaim == null ? null : runningClaim.task();
            if (task != null && IngestionTaskService.STATUS_COMPLETED.equals(task.getStatus())) {
                log.info(
                        "event=mq_consume_completed topic={} action={} documentId={} taskId={} reason=task_already_completed costMs={}",
                        ragProperties.ingestionTopic(),
                        action,
                        message.documentId(),
                        message.taskId(),
                        elapsedMillis(startedAt)
                );
                return;
            }
            if (task != null && IngestionTaskService.STATUS_DEAD.equals(task.getStatus())) {
                log.warn(
                        "event=mq_consume_dead_waiting_dlq topic={} action={} documentId={} taskId={} costMs={}",
                        ragProperties.ingestionTopic(),
                        action,
                        message.documentId(),
                        message.taskId(),
                        elapsedMillis(startedAt)
                );
                throw new IllegalStateException("任务已达到最大重试次数，等待 RocketMQ 投递到死信队列");
            }
            if (task != null && IngestionTaskService.STATUS_FAILED.equals(task.getStatus())) {
                log.info(
                        "event=mq_consume_completed topic={} action={} documentId={} taskId={} reason=task_failed_waiting_manual_retry costMs={}",
                        ragProperties.ingestionTopic(),
                        action,
                        message.documentId(),
                        message.taskId(),
                        elapsedMillis(startedAt)
                );
                return;
            }
            if (task != null && !runningClaim.acquired()) {
                log.info(
                        "event=mq_consume_waiting topic={} action={} documentId={} taskId={} status={} reason=task_not_claimed costMs={}",
                        ragProperties.ingestionTopic(),
                        action,
                        message.documentId(),
                        message.taskId(),
                        task.getStatus(),
                        elapsedMillis(startedAt)
                );
                throw new IllegalStateException("任务正在执行，稍后重试");
            }

            if (IngestionTaskMessage.ACTION_REBUILD_VECTORS.equals(action)) {
                IngestionExecutionResult result = documentIngestionService.rebuildDocumentVectors(message.documentId());
                handleExecutionResult(message, result);
                log.info(
                        "event=mq_consume_completed topic={} action={} documentId={} taskId={} costMs={}",
                        ragProperties.ingestionTopic(),
                        action,
                        message.documentId(),
                        message.taskId(),
                        elapsedMillis(startedAt)
                );
                return;
            }

            ChunkingOptions options = ChunkingOptions.from(
                    ragProperties,
                    message.strategy(),
                    message.chunkSize(),
                    message.chunkOverlap(),
                    message.maxChunks()
            );
            IngestionExecutionResult result = documentIngestionService.processDocument(message.documentId(), options);
            handleExecutionResult(message, result);
            log.info(
                    "event=mq_consume_completed topic={} action={} documentId={} taskId={} costMs={}",
                    ragProperties.ingestionTopic(),
                    action,
                    message.documentId(),
                    message.taskId(),
                    elapsedMillis(startedAt)
            );
        }
    }

    // 有任务编号时抢占任务处理权。
    private IngestionTaskService.RunningClaim claimTaskRunningIfPresent(IngestionTaskMessage message) {
        if (message.taskId() == null || message.taskId().isBlank()) {
            return null;
        }
        return ingestionTaskService.claimRunning(message.taskId());
    }

    // 根据执行结果决定 ACK、重试或标记失败。
    private void handleExecutionResult(IngestionTaskMessage message, IngestionExecutionResult result) {
        if (message.taskId() == null || message.taskId().isBlank()) {
            if (!result.success() && result.retryable()) {
                throw new IllegalStateException(result.message());
            }
            return;
        }
        if (result.success()) {
            ingestionTaskService.markCompleted(message.taskId());
            return;
        }
        if (!result.retryable()) {
            ingestionTaskService.markFailed(message.taskId(), result.message());
            return;
        }
        boolean exhausted = ingestionTaskService.markRetryableFailure(message.taskId(), result.message());
        if (exhausted) {
            documentIngestionService.markDocumentFailed(message.documentId(), "任务重试次数已耗尽：" + result.message());
        }
        throw new IllegalStateException(result.message());
    }

    // 获取入库消费并发许可。
    private RedisSemaphoreService.Permit acquireIngestionPermit(
            IngestionTaskMessage message,
            String action,
            ConcurrencyLimitProperties.Limit limit
    ) {
        try {
            return redisSemaphoreService.tryAcquire(
                            INGESTION_SEMAPHORE_NAME,
                            limit.maxPermits(),
                            limit.leaseTtl()
                    )
                    .orElseThrow(() -> {
                        log.warn(
                                "event=mq_concurrency_limited topic={} action={} documentId={} sourceRequestId={} maxPermits={}",
                                ragProperties.ingestionTopic(),
                                action,
                                message.documentId(),
                                message.resolvedRequestId(),
                                limit.maxPermits()
                        );
                        return new IllegalStateException("当前文档处理任务较多，稍后重试");
                    });
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error(
                    "event=mq_concurrency_unavailable topic={} action={} documentId={} sourceRequestId={} maxPermits={} type={} message={}",
                    ragProperties.ingestionTopic(),
                    action,
                    message.documentId(),
                    message.resolvedRequestId(),
                    limit.maxPermits(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            throw exception;
        }
    }

    private boolean bindRequestId(IngestionTaskMessage message) {
        String requestId = message.requestId();
        if (requestId == null || requestId.isBlank() || "-".equals(requestId)) {
            return false;
        }
        MDC.put(MDC_REQUEST_ID_KEY, requestId);
        return true;
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
