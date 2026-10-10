package com.merine.rebuild.agent.provider.persistence;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.Vendor;

/** 使用侧模型选项的 SQL 投射：推理强度是逗号分隔的原始列，由服务层转成枚举列表。 */
public record ProviderModelOptionRow(
        String id,
        String providerId,
        Vendor vendor,
        String providerName,
        String modelId,
        String displayName,
        String reasoningEfforts) {}
