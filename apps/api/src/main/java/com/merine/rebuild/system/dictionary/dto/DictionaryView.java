package com.merine.rebuild.system.dictionary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 字典（类型 + 字典项）。
 *
 * 所有字典同源同形：系统枚举（状态、菜单类型、单位层级）由迁移写入引导数据，
 * 之后和业务字典一样可以在线维护，标签、说明、排序与状态都可改。
 */
public record DictionaryView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "字典编码，形如 common.status 或 vessel.type")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "字典说明；未填写为 null", nullable = true) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "字典状态：ENABLED 启用，DISABLED 停用；停用的字典不返回字典项")
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "编辑版本")
        int version,
        @Schema(description = "最近修改时刻（UTC）", nullable = true)
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "字典项，按排序升序，含停用项")
        List<DictionaryItemView> items) {
}
