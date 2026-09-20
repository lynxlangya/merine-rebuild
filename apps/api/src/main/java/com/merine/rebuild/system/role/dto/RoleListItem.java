package com.merine.rebuild.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 角色管理列表的一行。
 *
 * 内置标记由服务端按配置的角色编码判定，前端据此禁用删除与权限编辑，
 * 但真正的保护在后端判定。
 */
public record RoleListItem(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "角色说明；未填写为 null", nullable = true) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "角色状态：ENABLED 启用，DISABLED 停用")
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "是否内置管理员角色：不可删除，恒拥有全部权限")
        boolean builtin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "已分配的功能权限数；内置角色为全部权限码数量")
        int permissionCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "持有该角色的账号数，含已停用账号")
        long userCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "编辑版本；保存时原样提交，冲突须重新读取")
        int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "最近修改时刻（UTC）")
        Instant updatedAt) {
}
