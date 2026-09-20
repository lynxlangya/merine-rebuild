package com.merine.rebuild.system.user.support;

import java.util.List;

/**
 * 拆分 GROUP_CONCAT 聚合出来的列。
 *
 * 供用户模块的登录查询与管理列表复用；不依赖调用方或公开业务模型。
 */
public final class AggregatedColumns {

    private AggregatedColumns() {
    }

    /** 空值与空串都返回空列表，而不是含一个空串的列表。 */
    public static List<String> split(String aggregated) {
        return aggregated == null || aggregated.isBlank() ? List.of() : List.of(aggregated.split(","));
    }
}
