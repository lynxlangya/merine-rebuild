package com.merine.rebuild.system.role.dto;

import com.merine.rebuild.system.user.usage.RoleHolder;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色成员（只读）。
 *
 * 授权关系仍只在用户管理里写：这里只回答「谁在用这个角色」，
 * 供删除判断与排查使用，不提供增删成员的接口。
 */
public record RoleMember(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户技术主键，十进制字符串")
        String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String loginName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unitName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "账号状态：ENABLED 启用，DISABLED 停用")
        String status) {

    public static RoleMember from(RoleHolder holder) {
        return new RoleMember(Long.toString(holder.id()), holder.loginName(), holder.displayName(),
                holder.unitName(), holder.status());
    }
}
