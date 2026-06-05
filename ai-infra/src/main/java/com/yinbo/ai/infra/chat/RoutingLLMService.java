package com.yinbo.ai.infra.chat;

import com.yinbo.ai.api.chat.LLMRequest;
import com.yinbo.ai.api.chat.LLMResponse;
import com.yinbo.ai.api.chat.LLMService;
import com.yinbo.ai.api.chat.StreamCallback;
import com.yinbo.ai.infra.enums.ModelCapability;
import com.yinbo.ai.infra.http.ModelClientCommittedException;
import com.yinbo.ai.infra.http.ModelClientStreamClosedException;
import com.yinbo.ai.infra.model.ModelRoutingExecutor;
import com.yinbo.ai.infra.model.ModelSelector;
import com.yinbo.ai.infra.model.ModelTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Primary
@Service
// 带模型路由和故障转移的 LLM 服务。
public class RoutingLLMService implements LLMService {

    private final ModelSelector modelSelector;
    private final ModelRoutingExecutor routingExecutor;
    private final Map<String, ChatClient> chatClients;

    // 注入模型选择器、执行器和所有 Chat 客户端。
    public RoutingLLMService(
            ModelSelector modelSelector,
            ModelRoutingExecutor routingExecutor,
            List<ChatClient> chatClients
    ) {
        this.modelSelector = modelSelector;
        this.routingExecutor = routingExecutor;
        this.chatClients = chatClients.stream().collect(Collectors.toMap(ChatClient::provider, Function.identity()));
    }

    @Override
    // 执行非流式路由调用。
    public LLMResponse chat(LLMRequest request) {
        List<ModelTarget> targets = modelSelector.selectChatCandidates(request.modelId(), request.thinkMode());
        return routingExecutor.executeWithFallback(
                ModelCapability.CHAT,
                targets,
                target -> chatClients.get(target.providerId()),
                (client, target) -> client.chat(request, target)
        );
    }

    @Override
    // 执行流式路由调用。
    public LLMResponse streamChat(LLMRequest request, StreamCallback callback) {
        List<ModelTarget> targets = modelSelector.selectChatCandidates(request.modelId(), request.thinkMode());
        return routingExecutor.executeWithFallback(
                ModelCapability.CHAT,
                targets,
                target -> chatClients.get(target.providerId()),
                (client, target) -> {
                    ProbeBufferingCallback probeCallback = new ProbeBufferingCallback(callback);
                    try {
                        return client.streamChat(request, probeCallback, target);
                    } catch (RuntimeException exception) {
                        if (ModelClientStreamClosedException.causedBy(exception)) {
                            throw exception;
                        }
                        if (probeCallback.committed()) {
                            throw new ModelClientCommittedException("模型流式输出已开始，不能切换候选模型", exception);
                        }
                        throw exception;
                    }
                }
        );
    }

    // 首包探测期间缓冲输出，收到首个 token 后再提交给真实回调。
    private static final class ProbeBufferingCallback implements StreamCallback {

        private final StreamCallback delegate;
        private final List<String> bufferedDeltas = new ArrayList<>();
        private boolean committed;

        private ProbeBufferingCallback(StreamCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onDelta(String delta) {
            if (!committed) {
                bufferedDeltas.add(delta);
                committed = true;
                bufferedDeltas.forEach(delegate::onDelta);
                bufferedDeltas.clear();
                return;
            }
            delegate.onDelta(delta);
        }

        private boolean committed() {
            return committed;
        }
    }
}
