package com.merine.rebuild.maritime.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 警务资源页的最小责任关系摘要；不是码头完整档案。 */
public record WharfRelationView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String responsibleOfficerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String responsibleOfficerName
) {}
