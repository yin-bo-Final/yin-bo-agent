package com.yinbo.mcp.logistics;

// 供应商查询异常，区分“需要用户补参数”和真正失败。
public class LogisticsProviderException extends RuntimeException {

    private final boolean needClarification;
    private final String userMessage;

    private LogisticsProviderException(boolean needClarification, String userMessage, String detailMessage, Throwable cause) {
        super(detailMessage, cause);
        this.needClarification = needClarification;
        this.userMessage = userMessage;
    }

    public static LogisticsProviderException clarification(String userMessage) {
        return new LogisticsProviderException(true, userMessage, userMessage, null);
    }

    public static LogisticsProviderException failure(String userMessage, String detailMessage) {
        return new LogisticsProviderException(false, userMessage, detailMessage, null);
    }

    public static LogisticsProviderException failure(String userMessage, String detailMessage, Throwable cause) {
        return new LogisticsProviderException(false, userMessage, detailMessage, cause);
    }

    public boolean needClarification() {
        return needClarification;
    }

    public String userMessage() {
        return userMessage;
    }
}
