package com.merine.rebuild.bootstrap;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

public record BootstrapStatus(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String application,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String database,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant serverTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalProbes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProbeRecord> recentProbes) {
}
