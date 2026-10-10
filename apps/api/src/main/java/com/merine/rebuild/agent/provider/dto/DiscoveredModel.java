package com.merine.rebuild.agent.provider.dto;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 上游模型清单里的一条：模型标识、归属方，以及该模型官方支持的推理强度。
 * 强度取自本地的官方取值目录（上游 /models 不返回能力信息），目录没收录时为空白，
 * 表示该项目前不提供档位选择，也不向上游发送该参数。
 */
@Schema(name = "DiscoveredProviderModel")
public record DiscoveredModel(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelId,
        @Schema(description = "上游返回的归属方，可能为空") String ownedBy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReasoningEffort> reasoningEfforts) {}
