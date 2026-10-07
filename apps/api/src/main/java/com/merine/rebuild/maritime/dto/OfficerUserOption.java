package com.merine.rebuild.maritime.dto;
import io.swagger.v3.oas.annotations.media.Schema;
/** 最小关联显示信息，不开放完整管理档案。 */
public record OfficerUserOption(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String loginName) {}
