package com.merine.rebuild.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 可选导航图标：前端已注册的图标清单，菜单里的目录与页面节点只能从这里选。 */
public record IconOption(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "图标名称，与前端 iconRegistry 注册的组件一致")
        String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "图标含义，便于选择")
        String label) {
}
