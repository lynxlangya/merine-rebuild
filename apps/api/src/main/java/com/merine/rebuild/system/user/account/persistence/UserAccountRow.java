package com.merine.rebuild.system.user.account.persistence;

/** 登录查询的数据库投射；SQL 聚合格式只在 user 模块内部使用。 */
public record UserAccountRow(
        long id,
        String loginName,
        String displayName,
        String passwordHash,
        String unitName,
        String unitStatus,
        String accountStatus,
        int authorizationVersion,
        String aggregatedRoleCodes,
        String aggregatedRoleNames) {
}
