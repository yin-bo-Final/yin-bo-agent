package com.yinbo.agent.ingestion.queue;

import com.yinbo.agent.common.service.RedisSemaphoreService;
import com.yinbo.agent.config.ConcurrencyLimitProperties;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.model.ChunkingOptions;
import com.yinbo.agent.ingestion.service.DocumentIngestionService;
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
        consumerGroup = "${app.ai.rag.ingestion-consumer-group:yinbo-agent-ingestion-consumer}"
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

    // 注入 RAG 配置、并发控制和文档入库服务。
    public DocumentIngestionTaskConsumer(
            RagProperties ragProperties,
            ConcurrencyLimitProperties concurrencyLimitProperties,
            RedisSemaphoreService redisSemaphoreService,
            DocumentIngestionService documentIngestionService
    ) {
        this.ragProperties = ragProperties;
        this.concurrencyLimitProperties = concurrencyLimitProperties;
        this.redisSemaphoreService = redisSemaphoreService;
        this.documentIngestionService = documentIngestionService;
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
            if (IngestionTaskMessage.ACTION_REBUILD_VECTORS.equals(action)) {
                documentIngestionService.rebuildDocumentVectors(message.documentId());
                log.info(
                        "event=mq_consume_completed topic={} action={} documentId={} costMs={}",
                        ragProperties.ingestionTopic(),
                        action,
                        message.documentId(),
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
            documentIngestionService.processDocument(message.documentId(), options);
            log.info(
                    "event=mq_consume_completed topic={} action={} documentId={} costMs={}",
                    ragProperties.ingestionTopic(),
                    action,
                    message.documentId(),
                    elapsedMillis(startedAt)
            );
        }
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
