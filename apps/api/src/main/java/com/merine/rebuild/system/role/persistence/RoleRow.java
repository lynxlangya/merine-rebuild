package com.merine.rebuild.system.role.persistence;

import java.time.Instant;

/**
 * 角色行的数据库投射。
 *
 * permissionCount 是已写入 sys_role_permission 的授予数；内置角色的实际权限是全部权限码，
 * 由服务层补全，因此这里的数字对内置角色只表示「库里存了几行」。
 */
public record RoleRow(
        long id,
        String code,
        String name,
        String description,
        String status,
        int version,
        Instant updatedAt,
        int permissionCount) {
}
