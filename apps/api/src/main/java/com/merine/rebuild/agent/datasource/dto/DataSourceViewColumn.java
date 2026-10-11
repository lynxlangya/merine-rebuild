package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 目标库返回的单个字段：名称、类型、可空性与列注释。
 * 注释只用于界面展示与模型默认说明，不作为权限依据。
 */
@Schema(name = "DataSourceViewColumn")
public record DataSourceViewColumn(
        String name,
        String type,
        boolean nullable,
        String comment) {
}
