package com.merine.rebuild.maritime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 关联维护的最小候选项，不暴露警务完整档案。 */
public record ArchiveOption(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String policeStationId
) {}
