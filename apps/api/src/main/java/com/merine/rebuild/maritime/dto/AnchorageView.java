package com.merine.rebuild.maritime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AnchorageView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String region,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String location,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String purpose,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String fixtureKey) {}
