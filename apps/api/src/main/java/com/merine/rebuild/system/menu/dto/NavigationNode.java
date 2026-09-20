package com.merine.rebuild.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 当前账号可见的导航节点。
 *
 * 只包含已授权、且未停用的节点；目录在没有任何可见子节点时不下发。
 * 页面用 routeKey 关联前端注册表取组件与图标，后端不存前端路径。
 */
public record NavigationNode(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "节点类型：DIRECTORY 目录，PAGE 页面，TAB 页签，BUTTON 按钮")
        String type,
        @Schema(description = "前端已注册的路由 key；仅页面节点有值", nullable = true) String routeKey,
        @Schema(description = "节点说明；未填写为 null", nullable = true) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<NavigationNode> children) {
}
