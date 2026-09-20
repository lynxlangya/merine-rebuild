package com.merine.rebuild.system.dictionary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 字典项。
 *
 * value 是业务数据里保存的那个值，创建后不可修改；label、说明、排序与状态可改。
 * 读接口会连同**停用项**一起返回（带 status），这样历史数据仍能解析出标签，
 * 而选择项由前端按 status 过滤。
 */
public record DictionaryItemView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "字典项取值，在所属字典内唯一，创建后不可修改")
        String value,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
        @Schema(description = "字典项说明；未填写为 null", nullable = true) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sortOrder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "字典项状态：ENABLED 启用，DISABLED 停用")
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "编辑版本；保存时原样提交，冲突须重新读取")
        int version) {
}
