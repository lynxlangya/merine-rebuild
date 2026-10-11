package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 查询范围详情：数据源配置摘要 + 已保存的说明覆盖值。
 * 不主动连接目标库；视图/字段是否存在由 {@code /inspect} 的探测结果判断。
 */
@Schema(name = "QueryScopeDetail")
public record QueryScopeDetail(
        DataSourceView source,
        List<ScopeViewMeta> meta) {

    public static QueryScopeDetail of(DataSourceView source, List<ScopeViewMeta> meta) {
        return new QueryScopeDetail(source, List.copyOf(meta));
    }
}
