package com.merine.rebuild.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException error,
                                               HttpServletRequest request) {
        var field = error.getBindingResult().getFieldError();
        String message = field == null ? "请求参数不正确" : field.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", message, request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> invalidJson(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_JSON", "请求内容格式不正确", request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(HttpServletRequest request) {
        return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", "请求的资源不存在", request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpServletRequest request) {
        return ResponseEntity.status(405).body(ApiResponse.error("METHOD_NOT_ALLOWED", "请求方法不支持", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error, HttpServletRequest request) {
        log.error("Request failed: {}", request.getRequestURI(), error);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("INTERNAL_ERROR", "服务暂不可用，请稍后重试", request));
    }
}
