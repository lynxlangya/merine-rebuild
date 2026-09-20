package com.merine.rebuild.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 菜单节点（管理视角，含停用节点）。
 *
 * 同一个结构同时服务两处：菜单管理页展示整棵树；角色编辑抽屉把它当作权限勾选树
 * （页面/页签/按钮节点带权限码，目录只是分组）。
 */
public record MenuNode(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(description = "上级节点 id；顶层为 null", nullable = true) String parentId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "节点类型：DIRECTORY 目录，PAGE 页面，TAB 页签，BUTTON 按钮")
        String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "前端已注册的路由 key；仅页面节点有值", nullable = true) String routeKey,
        @Schema(description = "权限码；目录为 null", nullable = true) String permissionCode,
        @Schema(description = "节点说明；未填写为 null", nullable = true) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sortOrder,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "节点状态：ENABLED 启用，DISABLED 停用；停用只影响导航与可分配性")
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "编辑版本；保存时原样提交，冲突须重新读取")
        int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "最近修改时刻（UTC）")
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "下级节点，按排序升序")
        List<MenuNode> children) {
}
