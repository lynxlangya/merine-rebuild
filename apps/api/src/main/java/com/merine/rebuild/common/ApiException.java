package com.merine.rebuild.common;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * 有明确 HTTP 状态与稳定错误码的业务异常。
 * 各模块抛出它表达“可预期的失败”，由 {@link ApiExceptionHandler} 统一转换；
 * common 因此不需要认识任何业务模块的异常类型。
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<ApiResponse.FieldError> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message,
                        List<ApiResponse.FieldError> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<ApiResponse.FieldError> fieldErrors() {
        return fieldErrors;
    }
}
