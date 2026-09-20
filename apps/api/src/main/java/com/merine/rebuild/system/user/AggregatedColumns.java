package com.merine.rebuild.system.user;

import java.util.List;

/**
 * 拆分 GROUP_CONCAT 聚合出来的列。
 *
 * 登录用的 {@link UserAccount} 与列表用的 {@link UserRow} 都要做同一件事，
 * 之前各留了一份私有实现，改一处漏一处。这里放一份。
 */
final class AggregatedColumns {

    private AggregatedColumns() {
    }

    /** 空值与空串都返回空列表，而不是含一个空串的列表。 */
    static List<String> split(String aggregated) {
        return aggregated == null || aggregated.isBlank() ? List.of() : List.of(aggregated.split(","));
    }
}
