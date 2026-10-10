package com.merine.rebuild.agent.chat.dto;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 会话条目：列表与详情共用；只包含归属用户自己的会话。 */
@Schema(name = "ChatConversation")
public record ConversationView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastProviderId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastProviderName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lastModelId,
        ReasoningEffort lastReasoningEffort,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int messageCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {}
