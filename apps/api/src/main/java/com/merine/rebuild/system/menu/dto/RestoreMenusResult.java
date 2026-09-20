package com.merine.rebuild.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 恢复默认菜单的结果：只报新建数量，已有节点与权限码一律保持原样。 */
public record RestoreMenusResult(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "新建的菜单节点数")
        int createdMenus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "新建的权限码数")
        int createdPermissions) {
}
