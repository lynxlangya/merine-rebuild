package com.merine.rebuild.system.dictionary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 字典管理页的类型列表行。 */
public record DictionaryListItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "字典说明；未填写为 null", nullable = true) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "字典状态：ENABLED 启用，DISABLED 停用")
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "字典项数量（含停用项）")
        int itemCount,
        @Schema(description = "最近修改时刻（UTC）", nullable = true)
        Instant updatedAt) {
}
