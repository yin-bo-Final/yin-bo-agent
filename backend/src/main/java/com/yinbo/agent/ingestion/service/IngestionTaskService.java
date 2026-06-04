package com.yinbo.agent.ingestion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.ingestion.entity.IngestionTask;
import com.yinbo.agent.ingestion.entity.KnowledgeDocument;
import com.yinbo.agent.ingestion.mapper.IngestionTaskMapper;
import com.yinbo.agent.ingestion.model.ChunkingOptions;
import com.yinbo.agent.ingestion.queue.IngestionTaskMessage;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
// 文档入库任务状态服务。
public class IngestionTaskService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_RETRYING = "RETRYING";
    public static final String STATUS_DEAD = "DEAD";
    public static final int DEFAULT_MAX_RETRIES = 10;

    private static final Logger log = LoggerFactory.getLogger(IngestionTaskService.class);
    private static final String MDC_REQUEST_ID_KEY = "requestId";

    private final IngestionTaskMapper ingestionTaskMapper;

    // 注入任务 Mapper。
    public IngestionTaskService(IngestionTaskMapper ingestionTaskMapper) {
        this.ingestionTaskMapper = ingestionTaskMapper;
    }

    // 创建待投递的入库任务。
    public IngestionTask createPendingTask(KnowledgeDocument document, String action, ChunkingOptions options) {
        return createPendingTask(UUID.randomUUID().toString(), document, action, options, currentRequestId());
    }

    // 使用外部预生成任务编号创建待投递任务。
    public IngestionTask createPendingTask(
            String taskId,
            KnowledgeDocument document,
            String action,
            ChunkingOptions options,
            String sourceRequestId
    ) {
        IngestionTask task = new IngestionTask();
        task.setTaskNo(taskId);
        task.setDocumentId(document.getId());
        task.setDocumentNo(document.getDocumentNo());
        task.setKnowledgeBaseId(document.getKnowledgeBaseId());
        task.setAction(action);
        task.setStatus(STATUS_PENDING);
        if (options != null) {
            task.setStrategy(options.strategy().name());
            task.setChunkSize(options.chunkSize());
            task.setChunkOverlap(options.chunkOverlap());
            task.setMaxChunks(options.maxChunks());
        }
        task.setRetryCount(0);
        task.setMaxRetries(DEFAULT_MAX_RETRIES);
        task.setSourceRequestId(sourceRequestId);
        ingestionTaskMapper.insert(task);
        log.info(
                "event=ingestion_task_created taskId={} action={} documentId={} status={}",
                task.getTaskNo(),
                task.getAction(),
                task.getDocumentNo(),
                task.getStatus()
        );
        return task;
    }

    // 生成任务业务编号。
    public String newTaskId() {
        return UUID.randomUUID().toString();
    }

    // 标记任务消息已经投递到 MQ。
    public void markMqSent(String taskId, String mqMessageId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        ingestionTaskMapper.update(null, new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId)
                .set(IngestionTask::getMqMessageId, mqMessageId));
    }

    // 标记任务投递失败。
    public void markSendFailed(String taskId, String errorMessage) {
        markFinalFailure(taskId, STATUS_FAILED, errorMessage);
    }

    // 将任务抢占为运行中，只有抢占成功的消费者才允许执行耗时处理。
    public RunningClaim claimRunning(String taskId) {
        IngestionTask task = requireTask(taskId);
        if (STATUS_COMPLETED.equals(task.getStatus())
                || STATUS_DEAD.equals(task.getStatus())
                || STATUS_FAILED.equals(task.getStatus())) {
            return new RunningClaim(task, false);
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = ingestionTaskMapper.update(null, new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId)
                .and(wrapper -> wrapper
                        .eq(IngestionTask::getStatus, STATUS_PENDING)
                        .or()
                        .eq(IngestionTask::getStatus, STATUS_RETRYING))
                .set(IngestionTask::getStatus, STATUS_RUNNING)
                .set(IngestionTask::getLastStartedAt, now));
        IngestionTask latest = requireTask(taskId);
        return new RunningClaim(latest, updated > 0);
    }

    // 保留旧方法，兼容可能存在的内部调用。
    public IngestionTask markRunning(String taskId) {
        return claimRunning(taskId).task();
    }

    // 标记任务执行成功。
    public void markCompleted(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ingestionTaskMapper.update(null, new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId)
                .set(IngestionTask::getStatus, STATUS_COMPLETED)
                .set(IngestionTask::getLastError, null)
                .set(IngestionTask::getCompletedAt, now));
        log.info("event=ingestion_task_completed taskId={}", taskId);
    }

    // 标记任务不可重试失败。
    public void markFailed(String taskId, String errorMessage) {
        markFinalFailure(taskId, STATUS_FAILED, errorMessage);
    }

    // 记录可重试失败并返回是否已经耗尽重试次数。
    public boolean markRetryableFailure(String taskId, String errorMessage) {
        IngestionTask task = requireTask(taskId);
        int maxRetries = nullToDefault(task.getMaxRetries(), DEFAULT_MAX_RETRIES);
        int retryCount = nextRetryCount(task, maxRetries);
        String nextStatus = retryCount >= maxRetries ? STATUS_DEAD : STATUS_RETRYING;
        LocalDateTime now = LocalDateTime.now();
        ingestionTaskMapper.update(null, new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId)
                .set(IngestionTask::getStatus, nextStatus)
                .set(IngestionTask::getRetryCount, retryCount)
                .set(IngestionTask::getLastError, truncate(errorMessage, 1000))
                .set(IngestionTask::getLastFailedAt, now));
        log.warn(
                "event=ingestion_task_retryable_failed taskId={} status={} retryCount={} maxRetries={} message={}",
                taskId,
                nextStatus,
                retryCount,
                maxRetries,
                sanitizeLogValue(errorMessage)
        );
        return STATUS_DEAD.equals(nextStatus);
    }

    // 重置失败任务等待重新投递。
    public IngestionTask resetForRetry(String taskId) {
        IngestionTask task = requireTask(taskId);
        if (!STATUS_FAILED.equals(task.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有失败任务可以手动重试");
        }
        ingestionTaskMapper.update(null, new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId)
                .set(IngestionTask::getStatus, STATUS_PENDING)
                .set(IngestionTask::getLastError, null)
                .set(IngestionTask::getLastStartedAt, null)
                .set(IngestionTask::getLastFailedAt, null)
                .set(IngestionTask::getCompletedAt, null)
                .set(IngestionTask::getMqMessageId, null)
                .set(IngestionTask::getSourceRequestId, currentRequestId()));
        return requireTask(taskId);
    }

    // 删除失败任务记录。
    public void deleteFailedTask(String taskId) {
        IngestionTask task = requireTask(taskId);
        if (!STATUS_FAILED.equals(task.getStatus()) && !STATUS_DEAD.equals(task.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有失败任务可以删除");
        }
        ingestionTaskMapper.delete(new LambdaQueryWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId));
        log.info("event=ingestion_task_deleted taskId={} status={}", taskId, task.getStatus());
    }

    // 判断文档是否已经存在死信任务。
    public boolean hasDeadTaskForDocument(Long documentId) {
        if (documentId == null) {
            return false;
        }
        Long count = ingestionTaskMapper.selectCount(new LambdaQueryWrapper<IngestionTask>()
                .eq(IngestionTask::getDocumentId, documentId)
                .eq(IngestionTask::getStatus, STATUS_DEAD));
        return count != null && count > 0;
    }

    // 死信任务必须走后台人工处理，不能从文档入口再次投递。
    public void requireDocumentNotDead(Long documentId) {
        if (hasDeadTaskForDocument(documentId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档存在死信任务，请到失败任务页处理");
        }
    }

    // 判断任务记录是否已经存在，用于 RocketMQ 事务回查。
    public boolean exists(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        Long count = ingestionTaskMapper.selectCount(new LambdaQueryWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId));
        return count != null && count > 0;
    }

    // 判断失败任务重试本地事务是否已经提交。
    public boolean isRetryTransactionCommitted(String taskId, String sourceRequestId) {
        if (taskId == null || taskId.isBlank() || sourceRequestId == null || sourceRequestId.isBlank()) {
            return false;
        }
        IngestionTask task = ingestionTaskMapper.selectOne(new LambdaQueryWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId)
                .last("LIMIT 1"));
        return task != null && sourceRequestId.equals(task.getSourceRequestId());
    }

    // 根据任务编号获取任务。
    public IngestionTask requireTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "任务编号不能为空");
        }
        IngestionTask task = ingestionTaskMapper.selectOne(new LambdaQueryWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "入库任务不存在");
        }
        return task;
    }

    // 创建任务对应的 MQ 消息。
    public IngestionTaskMessage toMessage(IngestionTask task) {
        return new IngestionTaskMessage(
                task.getAction(),
                task.getTaskNo(),
                task.getDocumentNo(),
                task.getStrategy(),
                task.getChunkSize(),
                task.getChunkOverlap(),
                task.getMaxChunks(),
                currentRequestId()
        );
    }

    // 标记任务最终失败。
    private void markFinalFailure(String taskId, String status, String errorMessage) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        IngestionTask task = requireTask(taskId);
        int maxRetries = nullToDefault(task.getMaxRetries(), DEFAULT_MAX_RETRIES);
        int retryCount = nextRetryCount(task, maxRetries);
        String nextStatus = retryCount >= maxRetries ? STATUS_DEAD : status;
        ingestionTaskMapper.update(null, new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId)
                .set(IngestionTask::getStatus, nextStatus)
                .set(IngestionTask::getRetryCount, retryCount)
                .set(IngestionTask::getLastError, truncate(errorMessage, 1000))
                .set(IngestionTask::getLastFailedAt, now));
        log.warn(
                "event=ingestion_task_failed taskId={} status={} retryCount={} message={}",
                taskId,
                nextStatus,
                retryCount,
                sanitizeLogValue(errorMessage)
        );
    }

    // 从当前 MDC 读取 requestId。
    private String currentRequestId() {
        String requestId = MDC.get(MDC_REQUEST_ID_KEY);
        return requestId == null || requestId.isBlank() ? null : requestId;
    }

    // 空 Integer 视作 0。
    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    // 空 Integer 使用默认值。
    private int nullToDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    // 计算下一次失败次数，最多记录到上限。
    private int nextRetryCount(IngestionTask task, int maxRetries) {
        return Math.min(nullToZero(task.getRetryCount()) + 1, maxRetries);
    }

    // 裁剪过长文本。
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    // 清洗日志文本。
    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    // 任务抢占结果。
    public record RunningClaim(IngestionTask task, boolean acquired) {
    }
}
