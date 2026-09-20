package com.merine.rebuild.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 分页结果。页码从 1 开始；items 为空时返回空数组而不是 null。
 */
public record PageResult<T>(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<T> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long total,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pageSize) {

    public PageResult {
        items = List.copyOf(items);
    }
}
