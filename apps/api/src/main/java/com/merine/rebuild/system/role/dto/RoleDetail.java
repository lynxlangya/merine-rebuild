package com.merine.rebuild.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** 单个角色的完整视图：编辑抽屉与保存后的响应共用。 */
public record RoleDetail(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(description = "角色说明；未填写为 null", nullable = true) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "角色状态：ENABLED 启用，DISABLED 停用")
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "是否内置管理员角色：不可删除，且恒拥有全部权限")
        boolean builtin,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "已分配的功能权限码；内置角色返回全部权限码")
        List<String> permissionCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "持有该角色的账号数，含已停用账号")
        long userCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "编辑版本；保存时原样提交，冲突须重新读取")
        int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "最近修改时刻（UTC）")
        Instant updatedAt) {
}
