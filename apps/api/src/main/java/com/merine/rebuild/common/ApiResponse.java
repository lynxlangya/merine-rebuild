package com.merine.rebuild.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public record ApiResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) T data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String requestId,
        @Schema(description = "字段级校验错误；无字段错误时为 null")
        List<FieldError> fieldErrors) {

    /** 字段名与提示分开，前端据此把错误落回具体表单字段。 */
    public record FieldError(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {
    }

    public static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return new ApiResponse<>("OK", "成功", data, RequestIdFilter.requestId(request), null);
    }

    public static ApiResponse<Void> error(String code, String message, HttpServletRequest request) {
        return error(code, message, request, List.of());
    }

    public static ApiResponse<Void> error(String code, String message, HttpServletRequest request,
                                          List<FieldError> fieldErrors) {
        return new ApiResponse<>(code, message, null, RequestIdFilter.requestId(request),
                fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors));
    }
}
