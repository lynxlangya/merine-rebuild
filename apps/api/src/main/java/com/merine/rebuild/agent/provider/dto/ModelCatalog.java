package com.merine.rebuild.agent.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 拉取到的模型清单。上游的 /models 只描述「有哪些模型」，不描述能力，
 * 所以推理强度等配置不在响应里，导入后由使用侧挑选级别。
 */
@Schema(name = "ProviderModelCatalog")
public record ModelCatalog(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DiscoveredModel> models) {}
