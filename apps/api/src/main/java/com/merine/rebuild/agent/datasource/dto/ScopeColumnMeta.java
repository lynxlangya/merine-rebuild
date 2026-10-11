package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 字段说明覆盖值；空串表示回退目标库列注释。 */
@Schema(name = "ScopeColumnMeta")
public record ScopeColumnMeta(
        String name,
        String description) {
}
