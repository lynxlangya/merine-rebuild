package com.merine.rebuild.agent.provider.persistence;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.State;

/** 模型配置表的 SQL 投射；服务层按需要转成对外视图。 */
public record ProviderModelRow(
        String id,
        String providerId,
        String modelId,
        String displayName,
        String remark,
        String reasoningEfforts,
        State status,
        int sortOrder) {}
