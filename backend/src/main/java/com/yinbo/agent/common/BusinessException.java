package com.yinbo.agent.common;

import org.springframework.http.HttpStatus;

// 带 HTTP 状态码的业务异常。
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    // 创建业务异常。
    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    // 获取需要返回给前端的 HTTP 状态码。
    public HttpStatus getStatus() {
        return status;
    }
}
