package com.yinbo.ai.infra.http;

// 模型流式输出已经提交给前端后的异常。
public class ModelClientCommittedException extends RuntimeException {

    public ModelClientCommittedException(String message, Throwable cause) {
        super(message, cause);
    }
}
