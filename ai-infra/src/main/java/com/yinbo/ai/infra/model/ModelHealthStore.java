package com.yinbo.ai.infra.model;

import com.yinbo.ai.infra.config.AiModelProperties;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
// 模型健康状态和熔断器。
public class ModelHealthStore {

    private static final Logger log = LoggerFactory.getLogger(ModelHealthStore.class);

    private final AiModelProperties aiModelProperties;
    private final ConcurrentMap<String, HealthState> states = new ConcurrentHashMap<>();

    // 注入模型路由配置。
    public ModelHealthStore(AiModelProperties aiModelProperties) {
        this.aiModelProperties = aiModelProperties;
    }

    // 判断目标模型当前是否允许调用。
    public boolean allowCall(String targetId) {
        HealthState state = states.get(targetId);
        if (state == null || state.status == CircuitStatus.CLOSED) {
            return true;
        }
        long now = Instant.now().toEpochMilli();
        if (state.status == CircuitStatus.OPEN && now - state.openedAt >= openDurationMs()) {
            states.compute(targetId, (key, current) -> new HealthState(CircuitStatus.HALF_OPEN, current == null ? 0 : current.failures, now));
            return true;
        }
        return state.status == CircuitStatus.HALF_OPEN;
    }

    // 标记模型调用成功。
    public void markSuccess(String targetId) {
        states.compute(targetId, (key, current) -> {
            if (current != null && current.status != CircuitStatus.CLOSED) {
                log.info("event=ai_model_circuit_closed targetId={}", targetId);
            }
            return new HealthState(CircuitStatus.CLOSED, 0, 0);
        });
    }

    // 标记模型调用失败。
    public void markFailure(String targetId) {
        states.compute(targetId, (key, current) -> {
            int failures = current == null ? 1 : current.failures + 1;
            long now = Instant.now().toEpochMilli();
            if (failures >= failureThreshold()) {
                log.warn("event=ai_model_circuit_opened targetId={} failures={} openDurationMs={}", targetId, failures, openDurationMs());
                return new HealthState(CircuitStatus.OPEN, failures, now);
            }
            return new HealthState(CircuitStatus.CLOSED, failures, 0);
        });
    }

    private int failureThreshold() {
        return aiModelProperties.selection().resolvedFailureThreshold();
    }

    private long openDurationMs() {
        return aiModelProperties.selection().resolvedOpenDurationMs();
    }

    // 模型熔断状态。
    private enum CircuitStatus {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    // 单个模型健康快照。
    private static final class HealthState {

        private final CircuitStatus status;
        private final int failures;
        private final long openedAt;

        private HealthState(CircuitStatus status, int failures, long openedAt) {
            this.status = status;
            this.failures = failures;
            this.openedAt = openedAt;
        }
    }
}
