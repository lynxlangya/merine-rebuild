package com.merine.rebuild.system.user.usage;

/** 单个角色的持有账号数（含已停用账号，授予关系本身与账号状态无关）。 */
public record RoleHolderCount(String roleCode, long holderCount) {
}
