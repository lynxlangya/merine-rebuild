package com.merine.rebuild.agent.provider.dto;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.State;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 供应商连接下的模型：{@code modelId} 是调用时原样发送的供应商模型标识，
 * {@code displayName} 只影响界面显示；{@code reasoningEfforts} 是该模型可用的推理强度子集。
 */
@Schema(name = "ProviderModel")
public record ProviderModel(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String remark,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReasoningEffort> reasoningEfforts,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sortOrder) {}
