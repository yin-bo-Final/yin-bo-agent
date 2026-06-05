package com.yinbo.ai.infra.model;

import com.yinbo.ai.infra.enums.ModelCapability;
import com.yinbo.ai.infra.http.ModelClientCommittedException;
import com.yinbo.ai.infra.http.ModelClientException;
import com.yinbo.ai.infra.http.ModelClientStreamClosedException;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
// 模型故障转移执行器。
public class ModelRoutingExecutor {

    private static final Logger log = LoggerFactory.getLogger(ModelRoutingExecutor.class);

    private final ModelHealthStore modelHealthStore;

    // 注入模型健康状态仓库。
    public ModelRoutingExecutor(ModelHealthStore modelHealthStore) {
        this.modelHealthStore = modelHealthStore;
    }

    // 按候选优先级执行模型调用，失败时自动切换下一个候选。
    public <C, T> T executeWithFallback(
            ModelCapability capability,
            List<ModelTarget> targets,
            Function<ModelTarget, C> clientResolver,
            ModelCaller<C, T> caller
    ) {
        RuntimeException lastException = null;
        for (ModelTarget target : targets) {
            if (!modelHealthStore.allowCall(target.id())) {
                log.info("event=ai_model_skipped capability={} targetId={} reason=circuit_open", capability, target.id());
                continue;
            }
            C client = clientResolver.apply(target);
            if (client == null) {
                log.warn("event=ai_model_skipped capability={} targetId={} provider={} reason=client_missing", capability, target.id(), target.providerId());
                continue;
            }
            try {
                T result = caller.call(client, target);
                modelHealthStore.markSuccess(target.id());
                log.info("event=ai_model_call_succeeded capability={} targetId={} provider={}", capability, target.id(), target.providerId());
                return result;
            } catch (RuntimeException exception) {
                if (ModelClientStreamClosedException.causedBy(exception)) {
                    throw exception;
                }
                lastException = exception;
                modelHealthStore.markFailure(target.id());
                if (exception instanceof ModelClientCommittedException) {
                    throw exception;
                }
                log.warn(
                        "event=ai_model_call_failed capability={} targetId={} provider={} type={} message={}",
                        capability,
                        target.id(),
                        target.providerId(),
                        exception.getClass().getSimpleName(),
                        sanitizeLogValue(exception.getMessage())
                );
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new ModelClientException("所有候选模型都不可用：" + capability.name());
    }

    private String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }
}
