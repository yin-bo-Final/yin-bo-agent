package com.yinbo.ai.infra.http;

// 模型客户端统一异常。
public class ModelClientException extends RuntimeException {

    public ModelClientException(String message) {
        super(message);
    }

    public ModelClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
