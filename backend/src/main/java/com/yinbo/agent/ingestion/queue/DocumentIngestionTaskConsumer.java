package com.yinbo.agent.ingestion.queue;

import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.ChunkingOptions;
import com.yinbo.agent.ingestion.DocumentIngestionService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        topic = "${app.ai.rag.ingestion-topic:rag-ingestion-task}",
        consumerGroup = "${app.ai.rag.ingestion-consumer-group:yinbo-agent-ingestion-consumer}"
)
public class DocumentIngestionTaskConsumer implements RocketMQListener<IngestionTaskMessage> {

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
        String action = message.resolvedAction();
        if (IngestionTaskMessage.ACTION_REBUILD_VECTORS.equals(action)) {
            documentIngestionService.rebuildDocumentVectors(message.documentId());
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
    }
}
