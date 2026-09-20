package com.merine.rebuild.system.role;

import io.swagger.v3.oas.annotations.media.Schema;

/** 角色对外表示。对外引用用稳定编码 role_code；名称只用于展示。 */
public record RoleSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "角色编码，区分大小写，全局唯一")
        String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "角色状态：ENABLED 启用，DISABLED 停用")
        String status) {
}
