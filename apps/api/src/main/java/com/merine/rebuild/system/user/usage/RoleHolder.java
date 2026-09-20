package com.merine.rebuild.system.user.usage;

/**
 * 「谁持有这个角色」的只读行，供角色管理页面展示成员。
 *
 * 只带展示与判定需要的最小字段：账号、姓名、单位与账号状态；
 * 不含密码哈希，也不含任何业务数据。角色模块拿到后转成自己的对外 DTO。
 */
public record RoleHolder(
        long id,
        String loginName,
        String displayName,
        String unitName,
        String status) {
}
