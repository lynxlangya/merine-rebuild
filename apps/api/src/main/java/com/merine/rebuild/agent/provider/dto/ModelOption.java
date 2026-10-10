package com.merine.rebuild.agent.provider.dto;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Vendor;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 使用侧可见的模型选项：只暴露选择模型需要的标识与显示信息。
 * 地址、备注与密钥不进入该结果，避免把管理配置扩散给所有登录用户。
 */
@Schema(name = "ModelOption")
public record ModelOption(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String providerId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String providerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Vendor vendor,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ReasoningEffort> reasoningEfforts,
        /** 官方默认档位；不在可用集合里或目录没收录时为 null，由界面自行取舍。 */
        ReasoningEffort defaultReasoningEffort) {}
