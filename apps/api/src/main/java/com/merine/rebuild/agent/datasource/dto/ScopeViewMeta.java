package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 单个视图的说明覆盖值。显示名与说明为空表示未填写；
 * 字段列只包含有覆盖值的字段，默认注释由目标库实时返回。
 */
@Schema(name = "ScopeViewMeta")
public record ScopeViewMeta(
        String view,
        String displayName,
        String description,
        List<ScopeColumnMeta> columns) {
}
