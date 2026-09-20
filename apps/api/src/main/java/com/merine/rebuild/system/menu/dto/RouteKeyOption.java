package com.merine.rebuild.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 可选页面：前端已注册的 route key 清单，菜单里的页面节点只能从这里选。 */
public record RouteKeyOption(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "前端注册表里的路由 key")
        String key,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "页面名称，便于选择")
        String label) {
}
