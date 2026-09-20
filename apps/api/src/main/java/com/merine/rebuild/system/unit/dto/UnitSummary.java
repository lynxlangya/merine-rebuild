package com.merine.rebuild.system.unit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 单位对外表示。对外引用用稳定的业务编码 unit_code，不暴露技术主键，
 * 这样接口与调用方都不依赖自增 id。
 */
public record UnitSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "单位业务编码，区分大小写，全局唯一")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "单位状态：ENABLED 启用，DISABLED 停用")
        String status,
        @Schema(description = "上级单位编码；一级单位为 null", nullable = true)
        String parentCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "单位层级：1 总队，2 支队，3 大队")
        int level) {
}
