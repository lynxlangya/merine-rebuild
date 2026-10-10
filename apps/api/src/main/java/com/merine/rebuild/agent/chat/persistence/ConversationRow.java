package com.merine.rebuild.agent.chat.persistence;

import java.time.Instant;

/** 会话行：列表与详情共用；`userId` 只用于归属校验，不出接口。 */
public record ConversationRow(
        String id,
        long userId,
        String title,
        String lastProviderId,
        String lastProviderName,
        String lastModelId,
        String lastReasoningEffort,
        int messageCount,
        int version,
        Instant createdAt,
        Instant updatedAt) {}
