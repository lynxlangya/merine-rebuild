package com.merine.rebuild.agent.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.*;

/** 供应商连接对外视图：连接字段 + 其下的模型列表（按 sortOrder 排序）。 */
@Schema(name = "ModelProvider")
public record ProviderView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Vendor vendor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String remark,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String website,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String baseUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ProviderModel> models,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {}
