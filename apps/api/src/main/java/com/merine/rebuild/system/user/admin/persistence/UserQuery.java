package com.merine.rebuild.system.user.admin.persistence;

/**
 * 用户列表查询条件。keywordPattern 是已经转义并加上 % 的 LIKE 参数，
 * 由服务层生成——SQL 里不做字符串拼接，也不用 ${}。
 */
public record UserQuery(
        String keywordPattern,
        String unitCode,
        String roleCode,
        String status,
        int page,
        int pageSize) {

    /**
     * 用 long 计算偏移量：page 来自请求参数且只校验了下界，
     * 用 int 计算时 page 很大（约 2.1e7 以上）会溢出成负数，
     * 负 OFFSET 会被 MySQL 判成语法错误并变成 500，而不是预期的 400。
     */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
