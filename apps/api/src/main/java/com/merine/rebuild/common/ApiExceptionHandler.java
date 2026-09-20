package com.merine.rebuild.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 业务模块用 ApiException 表达可预期失败，这里只做协议转换。 */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> business(ApiException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status())
                .body(ApiResponse.error(error.code(), error.getMessage(), request, error.fieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException error,
                                               HttpServletRequest request) {
        List<ApiResponse.FieldError> fieldErrors = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiResponse.FieldError(field.getField(), field.getDefaultMessage()))
                .toList();
        String message = fieldErrors.isEmpty() ? "请求参数不正确" : fieldErrors.getFirst().message();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_ERROR", message, request, fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> invalidJson(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_JSON", "请求内容格式不正确", request));
    }

    /** 路径变量或查询参数类型不匹配（例如把非数字当成 id）属于请求错误，不是服务故障。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> typeMismatch(MethodArgumentTypeMismatchException error,
                                                   HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("INVALID_PARAMETER", "参数格式不正确：" + error.getName(), request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(HttpServletRequest request) {
        return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", "请求的资源不存在", request));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpServletRequest request) {
        return ResponseEntity.status(405).body(ApiResponse.error("METHOD_NOT_ALLOWED", "请求方法不支持", request));
    }

    /**
     * 未预期错误。
     *
     * ERROR 只记异常类型与所在位置，不打印异常对象：MyBatis/JDBC 的异常消息里会带上
     * 结果集字段值（实测出现过 `Cannot convert string '演示单位甲' to ...`），
     * 直接打印等于把库里的业务数据写进日志。完整堆栈放在 DEBUG，需要时临时打开日志级别。
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error, HttpServletRequest request) {
        log.error("Unhandled error: type={} uri={}", error.getClass().getName(), request.getRequestURI());
        if (log.isDebugEnabled()) {
            log.debug("Unhandled error detail: uri={}", request.getRequestURI(), error);
        }
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error("INTERNAL_ERROR", "服务暂不可用，请稍后重试", request));
    }
}
