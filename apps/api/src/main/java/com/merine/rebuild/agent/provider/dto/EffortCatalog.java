package com.merine.rebuild.agent.provider.dto;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 推理强度目录的查询结果。{@code covered} 表示该供应商有没有官方取值目录：
 * 有目录时，界面只展示目录里的档位（目录没收录的模型就是「不使用档位参数」）；
 * 没有目录的供应商（我们尚未整理官方文档的那些）保留自行选择的余地。
 */
@Schema(name = "ProviderEffortCatalog")
public record EffortCatalog(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean covered,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ModelEfforts> models) {

    @Schema(name = "ProviderModelEfforts")
    public record ModelEfforts(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReasoningEffort> reasoningEfforts,
            ReasoningEffort defaultReasoningEffort) {}
}
