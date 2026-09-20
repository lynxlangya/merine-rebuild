package com.merine.rebuild.system.user.admin.persistence;

import com.merine.rebuild.system.user.support.AggregatedColumns;
import java.time.Instant;
import java.util.List;

/**
 * 数据库投射：角色用 GROUP_CONCAT 聚合后是字符串，这里拆成列表再交给 UserSummary。
 * 两个聚合在 SQL 里都按 role_code 排序，因此 roleCodes 与 roleNames 下标对应。
 */
public record UserRow(
        long id,
        String loginName,
        String displayName,
        String unitCode,
        String unitName,
        String status,
        int version,
        Instant lastLoginAt,
        String aggregatedRoleCodes,
        String aggregatedRoleNames) {

    public List<String> roleCodes() {
        return AggregatedColumns.split(aggregatedRoleCodes);
    }

    public List<String> roleNames() {
        return AggregatedColumns.split(aggregatedRoleNames);
    }
}
