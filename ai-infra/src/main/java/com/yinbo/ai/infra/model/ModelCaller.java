package com.yinbo.ai.infra.model;

// 通用模型调用函数。
@FunctionalInterface
public interface ModelCaller<C, T> {

    // 对指定客户端和模型目标发起调用。
    T call(C client, ModelTarget target);
}
