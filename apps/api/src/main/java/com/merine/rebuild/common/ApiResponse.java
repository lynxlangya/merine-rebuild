package com.merine.rebuild.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;

public record ApiResponse<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) T data,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String requestId) {

    public static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return new ApiResponse<>("OK", "成功", data, RequestIdFilter.requestId(request));
    }

    public static ApiResponse<Void> error(String code, String message, HttpServletRequest request) {
        return new ApiResponse<>(code, message, null, RequestIdFilter.requestId(request));
    }
}
