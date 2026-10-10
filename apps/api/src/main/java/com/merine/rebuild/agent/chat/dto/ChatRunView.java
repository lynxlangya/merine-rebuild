package com.merine.rebuild.agent.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 一次执行的受限快照：用于同 key 重复提交、断连核对与停止结果。
 * 不包含请求正文全文，也不包含任何凭据；parts 最多保留上限内的定稿部件。
 */
@Schema(name = "ChatRunView")
public record ChatRunView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String idempotencyKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String generationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String clientConversationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ChatRunState status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancelRequested,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ChatPartView> parts,
        QuestionAnswerState question,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant startedAt,
        Instant finishedAt) {

    public enum ChatRunState { RUNNING, AWAITING_INPUT, SUCCEEDED, FAILED, ABORTED }

    public enum MessagePartStatus { STREAMING, DONE, FAILED, ABORTED }

    public record ChatPartView(String partId, MessagePart part, MessagePartStatus status) {}

    /** 追问的状态与已收到的答案；只在 AWAITING_INPUT 的轮次出现。 */
    public record QuestionAnswerState(String questionId, boolean answered, List<String> values,
                                      String freeText) {}
}
