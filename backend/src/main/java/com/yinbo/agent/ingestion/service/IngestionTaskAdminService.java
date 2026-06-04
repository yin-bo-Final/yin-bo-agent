package com.yinbo.agent.ingestion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yinbo.agent.common.BusinessException;
import com.yinbo.agent.config.RagProperties;
import com.yinbo.agent.ingestion.dto.IngestionTaskResponse;
import com.yinbo.agent.ingestion.entity.IngestionTask;
import com.yinbo.agent.ingestion.entity.KnowledgeDocument;
import com.yinbo.agent.ingestion.mapper.IngestionTaskMapper;
import com.yinbo.agent.ingestion.mapper.KnowledgeDocumentMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
// 管理后台入库任务服务。
public class IngestionTaskAdminService {

    private static final Logger log = LoggerFactory.getLogger(IngestionTaskAdminService.class);
    private static final String DOCUMENT_STATUS_PROCESSING = "PROCESSING";

    private final RagProperties ragProperties;
    private final IngestionTaskMapper ingestionTaskMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final IngestionTaskService ingestionTaskService;
    private final IngestionTaskProducerService ingestionTaskProducerService;

    // 注入任务、文档和 MQ 相关依赖。
    public IngestionTaskAdminService(
            RagProperties ragProperties,
            IngestionTaskMapper ingestionTaskMapper,
            KnowledgeDocumentMapper knowledgeDocumentMapper,
            IngestionTaskService ingestionTaskService,
            IngestionTaskProducerService ingestionTaskProducerService
    ) {
        this.ragProperties = ragProperties;
        this.ingestionTaskMapper = ingestionTaskMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.ingestionTaskService = ingestionTaskService;
        this.ingestionTaskProducerService = ingestionTaskProducerService;
    }

    // 查询失败或死信状态的入库任务。
    public List<IngestionTaskResponse> listFailedTasks() {
        return ingestionTaskMapper.selectList(new LambdaQueryWrapper<IngestionTask>()
                        .in(IngestionTask::getStatus, List.of(
                                IngestionTaskService.STATUS_FAILED,
                                IngestionTaskService.STATUS_DEAD
                        ))
                        .orderByDesc(IngestionTask::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 手动重试失败任务。
    public IngestionTaskResponse retryTask(String taskId) {
        IngestionTask task = ingestionTaskService.requireTask(taskId);
        KnowledgeDocument document = requireDocument(task.getDocumentId());
        if (DOCUMENT_STATUS_PROCESSING.equals(document.getStatus())) {
            throw new BusinessException(HttpStatus.CONFLICT, "文档正在处理中，请稍后再试");
        }

        ingestionTaskProducerService.sendRetryTransaction(task);
        log.info(
                "event=ingestion_task_retry_submitted topic={} action={} taskId={} documentId={}",
                ragProperties.ingestionTopic(),
                task.getAction(),
                task.getTaskNo(),
                task.getDocumentNo()
        );
        return toResponse(ingestionTaskService.requireTask(task.getTaskNo()));
    }

    // 删除失败任务记录。
    public void deleteTask(String taskId) {
        ingestionTaskService.deleteFailedTask(taskId);
    }

    // 根据主键获取文档。
    private KnowledgeDocument requireDocument(Long documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "任务关联的文档不存在");
        }
        return document;
    }

    // 转换后台任务响应。
    private IngestionTaskResponse toResponse(IngestionTask task) {
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(task.getDocumentId());
        return new IngestionTaskResponse(
                task.getTaskNo(),
                task.getAction(),
                task.getStatus(),
                task.getDocumentNo(),
                document == null ? "文档已删除" : document.getFileName(),
                document == null ? "DELETED" : document.getStatus(),
                task.getStrategy(),
                task.getChunkSize(),
                task.getChunkOverlap(),
                task.getMaxChunks(),
                cappedRetryCount(task),
                nullToDefault(task.getMaxRetries(), IngestionTaskService.DEFAULT_MAX_RETRIES),
                task.getLastError(),
                task.getSourceRequestId(),
                task.getMqMessageId(),
                toInstantOrNull(task.getLastStartedAt()),
                toInstantOrNull(task.getLastFailedAt()),
                toInstantOrNull(task.getCompletedAt()),
                toInstant(task.getCreatedAt()),
                toInstant(task.getUpdatedAt())
        );
    }

    // 空 Integer 视作 0。
    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    // 空 Integer 使用默认值。
    private int nullToDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    // 响应层兜底，不展示超过上限的历史脏数据。
    private int cappedRetryCount(IngestionTask task) {
        int retryCount = nullToZero(task.getRetryCount());
        int maxRetries = nullToDefault(task.getMaxRetries(), IngestionTaskService.DEFAULT_MAX_RETRIES);
        return Math.min(retryCount, maxRetries);
    }

    // 转换时间为响应时间。
    private Instant toInstant(LocalDateTime value) {
        return value == null ? Instant.now() : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    // 转换可空时间为响应时间。
    private Instant toInstantOrNull(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
