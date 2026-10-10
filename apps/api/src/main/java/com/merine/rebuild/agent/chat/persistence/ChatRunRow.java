package com.merine.rebuild.agent.chat.persistence;

import java.time.Instant;

/** 执行记录行：含用量与失败原因，不含请求正文。 */
public record ChatRunRow(
        String id,
        long userId,
        String conversationId,
        String idempotencyKey,
        String messageId,
        String generationId,
        String mode,
        String providerId,
        String providerName,
        String modelId,
        String reasoningEffort,
        String state,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt,
        int durationMs,
        int inputChars,
        int outputChars,
        Integer promptTokens,
        Integer completionTokens) {}
