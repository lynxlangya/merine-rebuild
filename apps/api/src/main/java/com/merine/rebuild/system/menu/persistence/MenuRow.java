package com.merine.rebuild.system.menu.persistence;

import java.time.Instant;

/** 菜单节点 + 权限码的数据库投射；树结构由服务层在内存里组装。 */
public record MenuRow(
        long id,
        Long parentId,
        String type,
        String name,
        String routeKey,
        Long permissionId,
        String permissionCode,
        String description,
        int sortOrder,
        String status,
        int version,
        Instant updatedAt) {
}
