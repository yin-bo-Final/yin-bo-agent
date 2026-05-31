package com.yinbo.agent.ingestion.queue;

import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.ChunkingOptions;
import com.yinbo.agent.ingestion.DocumentIngestionService;
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
public class DocumentIngestionTaskConsumer implements RocketMQListener<IngestionTaskMessage> {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionTaskConsumer.class);
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    private final RagProperties ragProperties;
    private final DocumentIngestionService documentIngestionService;

    public DocumentIngestionTaskConsumer(
            RagProperties ragProperties,
            DocumentIngestionService documentIngestionService
    ) {
        this.ragProperties = ragProperties;
        this.documentIngestionService = documentIngestionService;
    }

    @Override
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
            if (!IngestionTaskMessage.ACTION_CHUNK.equals(action)) {
                throw new IllegalArgumentException("不支持的文档处理任务：" + action);
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
