package com.merine.rebuild.system.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 审计页面的模块筛选项：名字来自代码侧注册表，不在字典里维护。 */
@Schema(name = "AuditModuleOption")
public record AuditModuleOption(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {}
