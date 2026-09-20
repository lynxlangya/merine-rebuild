package com.merine.rebuild.system.role.persistence;

/**
 * 角色列表查询条件。keywordPattern 是已转义并加上 % 的 LIKE 参数，由服务层生成，
 * SQL 里不做字符串拼接，也不用 ${}。
 */
public record RoleQuery(
        String keywordPattern,
        String status,
        int page,
        int pageSize) {

    /** 用 long 计算偏移量：page 只校验下界，int 运算会在大页码上溢出成负数。 */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
