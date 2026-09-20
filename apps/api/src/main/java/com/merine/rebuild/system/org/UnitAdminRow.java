package com.merine.rebuild.system.org;

import java.time.Instant;

/**
 * 单位树的数据库投射。
 *
 * 与 API 结构分开：持久化层带技术主键和数字型 parentId，
 * 对外只暴露稳定编码，避免接口依赖自增 id。
 */
public record UnitAdminRow(
        long id,
        String code,
        String name,
        Long parentId,
        String parentCode,
        int level,
        String areaCode,
        String status,
        int version,
        Instant updatedAt) {
}
