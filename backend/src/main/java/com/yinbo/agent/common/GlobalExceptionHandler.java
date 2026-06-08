package com.yinbo.agent.common;

import java.io.IOException;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
// 全局异常响应处理器。
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final String UNKNOWN_REQUEST_ID = "-";

    @ExceptionHandler(BusinessException.class)
    // 处理业务异常并返回业务状态码。
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException exception) {
        log.warn(
                "event=exception requestId={} type={} status={} message={}",
                requestId(),
                exception.getClass().getSimpleName(),
                exception.getStatus().value(),
                exception.getMessage()
        );
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponse(
                        exception.getStatus().value(),
                        exception.getMessage(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    // 处理参数校验异常。
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        log.warn(
                "event=exception requestId={} type={} status={} message={}",
                requestId(),
                exception.getClass().getSimpleName(),
                HttpStatus.BAD_REQUEST.value(),
                message
        );
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), message, Instant.now()));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    // 处理请求体、请求参数和类型转换异常。
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        String message = "请求参数不正确，请检查后重试";
        log.warn(
                "event=exception requestId={} type={} status={} message={}",
                requestId(),
                exception.getClass().getSimpleName(),
                HttpStatus.BAD_REQUEST.value(),
                sanitizeLogValue(exception.getMessage())
        );
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(HttpStatus.BAD_REQUEST.value(), message, Instant.now()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    // 处理文件大小超限异常。
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        log.warn(
                "event=exception requestId={} type={} status={} message={}",
                requestId(),
                exception.getClass().getSimpleName(),
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                exception.getMessage()
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiErrorResponse(
                        HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        "文件大小不能超过 200MB",
                        Instant.now()
                ));
    }

    @ExceptionHandler(MultipartException.class)
    // 处理 multipart 文件上传异常。
    public ResponseEntity<ApiErrorResponse> handleMultipart(MultipartException exception) {
        log.warn(
                "event=exception requestId={} type={} status={} message={}",
                requestId(),
                exception.getClass().getSimpleName(),
                HttpStatus.BAD_REQUEST.value(),
                exception.getMessage()
        );
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(
                        HttpStatus.BAD_REQUEST.value(),
                        "文件上传请求不正确，请重新选择文件后再上传",
                        Instant.now()
                ));
    }

    @ExceptionHandler(IOException.class)
    // 处理客户端主动断开连接，避免 SSE 已提交响应再写 JSON 错误体。
    public ResponseEntity<Void> handleIOException(IOException exception) {
        if (isClientDisconnected(exception)) {
            log.info(
                    "event=client_disconnected requestId={} type={} message={}",
                    requestId(),
                    exception.getClass().getSimpleName(),
                    sanitizeLogValue(exception.getMessage())
            );
            return ResponseEntity.noContent().build();
        }
        log.error(
                "event=exception requestId={} type={} status={} message={}",
                requestId(),
                exception.getClass().getSimpleName(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                exception.getMessage(),
                exception
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @ExceptionHandler(Exception.class)
    // 处理未预期的系统异常。
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        log.error(
                "event=exception requestId={} type={} status={} message={}",
                requestId(),
                exception.getClass().getSimpleName(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                exception.getMessage(),
                exception
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "服务暂时不可用，请稍后再试",
                        Instant.now()
                ));
    }

    // 读取当前线程 MDC 中的 requestId。
    private static String requestId() {
        String requestId = MDC.get(MDC_REQUEST_ID_KEY);
        return requestId == null ? UNKNOWN_REQUEST_ID : requestId;
    }

    // 清洗写入日志的异常文本。
    private static String sanitizeLogValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]", "_");
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    // 判断异常是否来自客户端主动关闭连接。
    private static boolean isClientDisconnected(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            if (className.contains("ClientAbortException") || className.contains("SocketException")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (
                    message.contains("Broken pipe")
                            || message.contains("Connection reset")
                            || message.contains("已建立的连接")
            )) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
