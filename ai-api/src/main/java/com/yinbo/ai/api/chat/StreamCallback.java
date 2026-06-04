package com.yinbo.ai.api.chat;

// LLM 流式回调。
public interface StreamCallback {

    // 推送一段模型输出。
    void onDelta(String delta);
}
