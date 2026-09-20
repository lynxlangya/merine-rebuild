package com.merine.rebuild.system.user.usage;

/** 单位直属用户数，用于单位管理的删除校验和详情展示。 */
public record UserUnitCount(long unitId, long userCount) {
}
