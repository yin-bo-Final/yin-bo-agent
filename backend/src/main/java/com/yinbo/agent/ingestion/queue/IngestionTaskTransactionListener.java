package com.yinbo.agent.ingestion.queue;

import com.yinbo.agent.ingestion.service.IngestionTaskService;
import com.yinbo.agent.ingestion.service.IngestionTaskTransactionService;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;

@RocketMQTransactionListener
// 文档入库事务消息监听器。
public class IngestionTaskTransactionListener implements RocketMQLocalTransactionListener {

    public static final String HEADER_TASK_ID = "ingestion_task_id";
    public static final String HEADER_DOCUMENT_ID = "ingestion_document_id";
    public static final String HEADER_ACTION = "ingestion_action";
    public static final String HEADER_TRANSACTION_TYPE = "ingestion_transaction_type";
    public static final String HEADER_REQUEST_ID = "ingestion_request_id";

    private static final Logger log = LoggerFactory.getLogger(IngestionTaskTransactionListener.class);

    private final IngestionTaskTransactionService transactionService;
    private final IngestionTaskService ingestionTaskService;

    // 注入本地事务服务和任务服务。
    public IngestionTaskTransactionListener(
            IngestionTaskTransactionService transactionService,
            IngestionTaskService ingestionTaskService
    ) {
        this.transactionService = transactionService;
        this.ingestionTaskService = ingestionTaskService;
    }

    @Override
    // 半消息发送成功后执行本地事务。
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object arg) {
        if (!(arg instanceof IngestionTaskTransactionCommand command)) {
            log.error("event=mq_transaction_local_failed reason=invalid_arg");
            return RocketMQLocalTransactionState.ROLLBACK;
        }
        try {
            transactionService.execute(command);
            log.info(
                    "event=mq_transaction_local_committed taskId={} documentId={} action={}",
                    command.taskId(),
                    command.documentId(),
                    command.action()
            );
            return RocketMQLocalTransactionState.COMMIT;
        } catch (RuntimeException exception) {
            log.warn(
                    "event=mq_transaction_local_rolled_back taskId={} documentId={} action={} type={} message={}",
                    command.taskId(),
                    command.documentId(),
                    command.action(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage()),
                    exception
            );
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    @Override
    // Broker 回查本地事务状态。
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        String taskId = stringHeader(message, HEADER_TASK_ID);
        if (taskId == null || taskId.isBlank()) {
            log.warn("event=mq_transaction_check_unknown reason=missing_task_id");
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        String transactionType = stringHeader(message, HEADER_TRANSACTION_TYPE);
        String requestId = stringHeader(message, HEADER_REQUEST_ID);
        boolean committed = IngestionTaskTransactionCommand.TYPE_RETRY.equals(transactionType)
                ? ingestionTaskService.isRetryTransactionCommitted(taskId, requestId)
                : ingestionTaskService.exists(taskId);
        log.info(
                "event=mq_transaction_checked taskId={} transactionType={} committed={}",
                taskId,
                transactionType,
                committed
        );
        return committed ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }

    // 读取字符串消息头。
    private String stringHeader(Message message, String key) {
        Object value = message.getHeaders().get(key);
        return value == null ? null : String.valueOf(value);
    }

    // 清洗日志文本。
    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("[\\r\\n\\t]", " ");
    }
}
