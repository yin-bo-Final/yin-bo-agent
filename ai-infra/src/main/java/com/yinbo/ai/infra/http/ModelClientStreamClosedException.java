package com.yinbo.ai.infra.http;

// 下游客户端主动断开流式连接。
public class ModelClientStreamClosedException extends RuntimeException {

    // 创建客户端断开异常。
    public ModelClientStreamClosedException(String message, Throwable cause) {
        super(message, cause);
    }

    // 判断异常链中是否包含客户端断开异常。
    public static boolean causedBy(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ModelClientStreamClosedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
