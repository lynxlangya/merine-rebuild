package com.merine.rebuild.agent.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * 一次执行的历史记录：用户、目标快照、终态、耗时与用量。
 * 不包含请求正文与凭据；`promptTokens`/`completionTokens` 在上游未返回 usage 时为 null。
 */
@Schema(name = "ChatRunRecord")
public record ChatRunRecordView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String conversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String providerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelId,
        ChatRunView.ChatRunState state,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String errorCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String errorMessage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant finishedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int durationMs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int inputChars,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int outputChars,
        Integer promptTokens,
        Integer completionTokens) {}
