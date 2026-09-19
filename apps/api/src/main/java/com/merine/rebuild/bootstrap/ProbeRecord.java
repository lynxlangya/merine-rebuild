package com.merine.rebuild.bootstrap;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record ProbeRecord(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String note,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
}
