package com.merine.rebuild.system.user.account;

import io.swagger.v3.oas.annotations.media.Schema;

/** 开发环境账号选择器的公开选项；按单位树层级展示，不含密码或权限事实。 */
public record DevLoginAccountOption(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String loginName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String unitName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1 总队，2 支队，3 大队")
        int unitLevel,
        @Schema(description = "直属上级单位名称；总队为 null", nullable = true)
        String parentUnitName) {
}
