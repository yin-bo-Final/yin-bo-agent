package com.yinbo.agent.ingestion.service;

import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.entity.IngestionTask;
import com.yinbo.agent.ingestion.model.ChunkingOptions;
import com.yinbo.agent.ingestion.queue.IngestionTaskMessage;
import com.yinbo.agent.ingestion.queue.IngestionTaskTransactionCommand;
import com.yinbo.agent.ingestion.queue.IngestionTaskTransactionListener;
import java.util.UUID;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Service
// 文档入库任务事务消息发送服务。
public class IngestionTaskProducerService {

    private static final Logger log = LoggerFactory.getLogger(IngestionTaskProducerService.class);
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    private final RagProperties ragProperties;
    private final RocketMQTemplate rocketMQTemplate;
    private final IngestionTaskService ingestionTaskService;

    // 注入 RAG 配置、RocketMQ 模板和任务服务。
    public IngestionTaskProducerService(
            RagProperties ragProperties,
            RocketMQTemplate rocketMQTemplate,
            IngestionTaskService ingestionTaskService
    ) {
        this.ragProperties = ragProperties;
        this.rocketMQTemplate = rocketMQTemplate;
        this.ingestionTaskService = ingestionTaskService;
    }

    // 提交分块事务消息。
    public String sendChunkTransaction(String documentId, ChunkingOptions options) {
        return sendTransaction(documentId, IngestionTaskMessage.ACTION_CHUNK, options);
    }

    // 提交重建向量事务消息。
    public String sendRebuildVectorsTransaction(String documentId) {
        return sendTransaction(documentId, IngestionTaskMessage.ACTION_REBUILD_VECTORS, null);
    }

    // 提交失败任务重试事务消息。
    public String sendRetryTransaction(IngestionTask task) {
        ChunkingOptions options = IngestionTaskMessage.ACTION_CHUNK.equals(task.getAction())
                ? ChunkingOptions.from(
                        ragProperties,
                        task.getStrategy(),
                        task.getChunkSize(),
                        task.getChunkOverlap(),
                        task.getMaxChunks()
                )
                : null;
        IngestionTaskMessage payload = new IngestionTaskMessage(
                task.getAction(),
                task.getTaskNo(),
                task.getDocumentNo(),
                task.getStrategy(),
                task.getChunkSize(),
                task.getChunkOverlap(),
                task.getMaxChunks(),
                currentRequestId()
        );
        sendTransaction(task.getTaskNo(), task.getDocumentNo(), task.getAction(), options, payload, IngestionTaskTransactionCommand.TYPE_RETRY);
        return task.getTaskNo();
    }

    // 发送事务半消息并等待本地事务结果。
    private String sendTransaction(String documentId, String action, ChunkingOptions options) {
        String taskId = ingestionTaskService.newTaskId();
        String requestId = currentRequestId();
        IngestionTaskMessage payload = new IngestionTaskMessage(
                action,
                taskId,
                documentId,
                options == null ? null : options.strategy().name(),
                options == null ? null : options.chunkSize(),
                options == null ? null : options.chunkOverlap(),
                options == null ? null : options.maxChunks(),
                requestId
        );
        sendTransaction(taskId, documentId, action, options, payload, IngestionTaskTransactionCommand.TYPE_START);
        return taskId;
    }

    // 发送事务半消息并等待本地事务结果。
    private void sendTransaction(
            String taskId,
            String documentId,
            String action,
            ChunkingOptions options,
            IngestionTaskMessage payload,
            String transactionType
    ) {
        String requestId = payload.requestId();
        Message<IngestionTaskMessage> message = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, taskId)
                .setHeader(IngestionTaskTransactionListener.HEADER_TASK_ID, taskId)
                .setHeader(IngestionTaskTransactionListener.HEADER_DOCUMENT_ID, documentId)
                .setHeader(IngestionTaskTransactionListener.HEADER_ACTION, action)
                .setHeader(IngestionTaskTransactionListener.HEADER_TRANSACTION_TYPE, transactionType)
                .setHeader(IngestionTaskTransactionListener.HEADER_REQUEST_ID, requestId)
                .build();
        IngestionTaskTransactionCommand command = new IngestionTaskTransactionCommand(
                transactionType,
                taskId,
                documentId,
                action,
                options,
                requestId
        );

        TransactionSendResult sendResult;
        try {
            sendResult = rocketMQTemplate.sendMessageInTransaction(ragProperties.ingestionTopic(), message, command);
        } catch (RuntimeException exception) {
            log.error(
                    "event=mq_transaction_send_failed topic={} action={} documentId={} taskId={} type={} message={}",
                    ragProperties.ingestionTopic(),
                    action,
                    documentId,
                    taskId,
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "分块任务事务消息发送失败，请检查 RocketMQ");
        }

        if (sendResult.getLocalTransactionState() != LocalTransactionState.COMMIT_MESSAGE) {
            log.warn(
                    "event=mq_transaction_not_committed topic={} action={} documentId={} taskId={} localState={}",
                    ragProperties.ingestionTopic(),
                    action,
                    documentId,
                    taskId,
                    sendResult.getLocalTransactionState()
            );
            throw new BusinessException(HttpStatus.CONFLICT, "任务提交失败，文档状态可能已变化，请刷新后重试");
        }

        ingestionTaskService.markMqSent(taskId, sendResult.getMsgId());
        log.info(
                "event=mq_transaction_sent topic={} action={} documentId={} taskId={} messageId={} sendStatus={}",
                ragProperties.ingestionTopic(),
                action,
                documentId,
                taskId,
                sendResult.getMsgId(),
                sendResult.getSendStatus()
        );
    }

    // 读取当前 requestId。
    private String currentRequestId() {
        String requestId = MDC.get(MDC_REQUEST_ID_KEY);
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    // 清洗日志文本。
    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("[\\r\\n\\t]", " ");
    }
}
