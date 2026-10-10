package com.merine.rebuild.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.ObjectMapper;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ObjectMapper json;

    public ApiExceptionHandler(ObjectMapper json) {
        this.json = json;
    }

    /**
     * 错误响应的唯一出口。
     *
     * <p>SSE 客户端只声明 {@code Accept: text/event-stream} 时收不下 JSON 错误体，
     * 内容协商会让整个响应失败（表现为 500）；这类请求直接写出 JSON 错误（状态码与格式不变），
     * 其余请求仍走正常的 ResponseEntity。只有 {@code agent:chat} 这个 SSE 端点会遇到这种声明。
     */
    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, ApiResponse<Void> body,
                                                      HttpServletRequest request,
                                                      HttpServletResponse response) throws IOException {
        if (!acceptsOnlyEventStream(request)) {
            return ResponseEntity.status(status).body(body);
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), body);
        return null;
    }

    private static boolean acceptsOnlyEventStream(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept == null || !accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) return false;
        return !(accept.contains(MediaType.APPLICATION_JSON_VALUE) || accept.contains("*/*"));
    }

    /** 业务模块用 ApiException 表达可预期失败，这里只做协议转换。 */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Void>> business(ApiException error, HttpServletRequest request,
                                               HttpServletResponse response) throws IOException {
        return respond(error.status(),
                ApiResponse.error(error.code(), error.getMessage(), request, error.fieldErrors()),
                request, response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException error,
                                               HttpServletRequest request,
                                               HttpServletResponse response) throws IOException {
        List<ApiResponse.FieldError> fieldErrors = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiResponse.FieldError(field.getField(), field.getDefaultMessage()))
                .toList();
        String message = fieldErrors.isEmpty() ? "请求参数不正确" : fieldErrors.getFirst().message();
        return respond(HttpStatus.BAD_REQUEST,
                ApiResponse.error("VALIDATION_ERROR", message, request, fieldErrors),
                request, response);
    }

    /** 查询参数或路径参数的约束校验（{@code @Min} 等）属于请求错误，统一在这里回 400。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ApiResponse<Void>> parameterValidation(HandlerMethodValidationException error,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response) throws IOException {
        Set<String> names = new LinkedHashSet<>();
        error.getParameterValidationResults().forEach(result ->
                names.add(result.getMethodParameter().getParameterName()));
        List<ApiResponse.FieldError> fieldErrors = new ArrayList<>();
        names.forEach(name -> fieldErrors.add(new ApiResponse.FieldError(name, "取值不符合要求")));
        return respond(HttpStatus.BAD_REQUEST,
                ApiResponse.error("VALIDATION_ERROR", "查询参数不符合要求", request, fieldErrors),
                request, response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> invalidJson(HttpServletRequest request,
                                                 HttpServletResponse response) throws IOException {
        return respond(HttpStatus.BAD_REQUEST,
                ApiResponse.error("INVALID_JSON", "请求内容格式不正确", request), request, response);
    }

    /** 路径变量或查询参数类型不匹配（例如把非数字当成 id）属于请求错误，不是服务故障。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> typeMismatch(MethodArgumentTypeMismatchException error,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) throws IOException {
        return respond(HttpStatus.BAD_REQUEST,
                ApiResponse.error("INVALID_PARAMETER", "参数格式不正确：" + error.getName(), request),
                request, response);
    }

    /** 删除等接口的必填版本参数缺失属于400，不能落入未预期异常。 */
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    ResponseEntity<ApiResponse<Void>> missingParameter(
            org.springframework.web.bind.MissingServletRequestParameterException error,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        return respond(HttpStatus.BAD_REQUEST,
                ApiResponse.error("INVALID_PARAMETER", "缺少必要参数：" + error.getParameterName(), request),
                request, response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(HttpServletRequest request,
                                              HttpServletResponse response) throws IOException {
        return respond(HttpStatus.NOT_FOUND,
                ApiResponse.error("NOT_FOUND", "请求的资源不存在", request), request, response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Void>> methodNotAllowed(HttpServletRequest request,
                                                       HttpServletResponse response) throws IOException {
        return respond(HttpStatus.METHOD_NOT_ALLOWED,
                ApiResponse.error("METHOD_NOT_ALLOWED", "请求方法不支持", request), request, response);
    }

    /**
     * 未预期错误。
     *
     * ERROR 只记异常类型与所在位置，不打印异常对象：MyBatis/JDBC 的异常消息里会带上
     * 结果集字段值（实测出现过 `Cannot convert string '演示单位甲' to ...`），
     * 直接打印等于把库里的业务数据写进日志。完整堆栈放在 DEBUG，需要时临时打开日志级别。
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error, HttpServletRequest request,
                                                 HttpServletResponse response) throws IOException {
        log.error("Unhandled error: type={} uri={}", error.getClass().getName(), request.getRequestURI());
        if (log.isDebugEnabled()) {
            log.debug("Unhandled error detail: uri={}", request.getRequestURI(), error);
        }
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                ApiResponse.error("INTERNAL_ERROR", "服务暂不可用，请稍后重试", request), request, response);
    }
}
