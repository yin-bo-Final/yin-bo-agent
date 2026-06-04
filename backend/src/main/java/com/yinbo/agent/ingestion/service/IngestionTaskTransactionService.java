package com.yinbo.agent.ingestion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.ingestion.entity.IngestionTask;
import com.yinbo.agent.ingestion.entity.KnowledgeDocument;
import com.yinbo.agent.ingestion.mapper.IngestionTaskMapper;
import com.yinbo.agent.ingestion.mapper.KnowledgeDocumentMapper;
import com.yinbo.agent.ingestion.model.ChunkingOptions;
import com.yinbo.agent.ingestion.queue.IngestionTaskMessage;
import com.yinbo.agent.ingestion.queue.IngestionTaskTransactionCommand;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
// RocketMQ 事务消息对应的本地事务服务。
public class IngestionTaskTransactionService {

    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final IngestionTaskMapper ingestionTaskMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final IngestionTaskService ingestionTaskService;

    // 注入文档 Mapper 和任务服务。
    public IngestionTaskTransactionService(
            IngestionTaskMapper ingestionTaskMapper,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            IngestionTaskService ingestionTaskService
    ) {
        this.ingestionTaskMapper = ingestionTaskMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.ingestionTaskService = ingestionTaskService;
    }

    @Transactional
    // CAS 抢占文档并创建待消费任务。
    public void execute(IngestionTaskTransactionCommand command) {
        if (IngestionTaskTransactionCommand.TYPE_RETRY.equals(command.type())) {
            executeRetry(command);
            return;
        }
        executeStart(command);
    }

    // 执行首次提交本地事务。
    private void executeStart(IngestionTaskTransactionCommand command) {
        KnowledgeDocument document = requireDocument(command.documentId());
        ingestionTaskService.requireDocumentNotDead(document.getId());

        int updated = updateDocumentToProcessing(command, document);
        if (updated != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档状态已变化，请刷新后重试");
        }

        ingestionTaskService.createPendingTask(
                command.taskId(),
                document,
                command.action(),
                command.options(),
                command.requestId()
        );
    }

    // 执行失败任务重试本地事务。
    private void executeRetry(IngestionTaskTransactionCommand command) {
        IngestionTask task = requireTask(command.taskId());
        if (!IngestionTaskService.STATUS_FAILED.equals(task.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有失败任务可以手动重试");
        }
        KnowledgeDocument document = requireDocument(task.getDocumentNo());
        int documentUpdated = knowledgeDocumentMapper.update(null, new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .eq(KnowledgeDocument::getStatus, STATUS_FAILED)
                .set(KnowledgeDocument::getStatus, STATUS_PROCESSING)
                .set(KnowledgeDocument::getErrorMessage, null));
        if (documentUpdated != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档状态已变化，请刷新后重试");
        }

        int taskUpdated = ingestionTaskMapper.update(null, new LambdaUpdateWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, command.taskId())
                .eq(IngestionTask::getStatus, IngestionTaskService.STATUS_FAILED)
                .set(IngestionTask::getStatus, IngestionTaskService.STATUS_PENDING)
                .set(IngestionTask::getLastError, null)
                .set(IngestionTask::getLastStartedAt, null)
                .set(IngestionTask::getLastFailedAt, null)
                .set(IngestionTask::getCompletedAt, null)
                .set(IngestionTask::getMqMessageId, null)
                .set(IngestionTask::getSourceRequestId, command.requestId()));
        if (taskUpdated != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "任务状态已变化，请刷新后重试");
        }
    }

    // 根据任务动作执行文档状态 CAS。
    private int updateDocumentToProcessing(IngestionTaskTransactionCommand command, KnowledgeDocument document) {
        String action = command.action();
        LambdaUpdateWrapper<KnowledgeDocument> update = new LambdaUpdateWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, document.getId())
                .set(KnowledgeDocument::getStatus, STATUS_PROCESSING)
                .set(KnowledgeDocument::getErrorMessage, null);

        if (IngestionTaskMessage.ACTION_REBUILD_VECTORS.equals(action)) {
            update.eq(KnowledgeDocument::getStatus, STATUS_COMPLETED);
            return knowledgeDocumentMapper.update(null, update);
        }

        ChunkingOptions options = command.options();
        update.in(KnowledgeDocument::getStatus, List.of(STATUS_UPLOADED, STATUS_COMPLETED))
                .set(KnowledgeDocument::getChunkStrategy, options.strategy().name())
                .set(KnowledgeDocument::getChunkSize, options.chunkSize())
                .set(KnowledgeDocument::getChunkOverlap, options.chunkOverlap())
                .set(KnowledgeDocument::getMaxChunks, options.maxChunks());
        return knowledgeDocumentMapper.update(null, update);
    }

    // 根据业务编号获取文档。
    private KnowledgeDocument requireDocument(String documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getDocumentNo, documentId)
                .last("LIMIT 1"));
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "文档不存在");
        }
        return document;
    }

    // 根据任务编号获取任务。
    private IngestionTask requireTask(String taskId) {
        IngestionTask task = ingestionTaskMapper.selectOne(new LambdaQueryWrapper<IngestionTask>()
                .eq(IngestionTask::getTaskNo, taskId)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "入库任务不存在");
        }
        return task;
    }
}
