package com.merine.rebuild.agent.chat.dto;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 对话请求。{@code requestId}（HTTP 诊断编号）不在这里：它由服务端生成。
 *
 * <p>{@code idempotencyKey} 是幂等标识；{@code clientConversationId} 只用于客户端关联，
 * 不作为授权依据；{@code generationId} 只在回答追问时出现。
 * 配置型上限（消息数、字符数等）由服务端按 {@code ChatProperties} 校验，
 * 这里的注解只做绝对上限，避免异常输入进入业务层。
 */
@Schema(name = "ChatRequest")
public record ChatRequest(
        @NotBlank @Size(max = 80) String idempotencyKey,
        @Size(max = 80) String generationId,
        @NotBlank @Size(max = 80) String clientConversationId,
        /** 服务端会话标识；省略时按首条提问新建会话。 */
        @Size(max = 80) String conversationId,
        @NotBlank @Size(max = 64) String clientTimeZone,
        @Size(max = 80) String providerId,
        @Size(max = 120) String modelId,
        /** 省略时交给供应商默认；NONE 表示关闭思考模式。 */
        ReasoningEffort reasoningEffort,
        @NotEmpty @Size(max = 200) @Valid List<ChatTurn> messages,
        @Size(max = 16) @Valid List<ChatAnswer> answers,
        @Valid ChatContext context) {

    public enum ChatRole { USER, ASSISTANT }

    public record ChatTurn(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ChatRole role,
                           @NotBlank @Size(max = 20000) String text) {}

    /** 自由文本在 answers 里单独表达；skipped=true 表示显式跳过该问题。 */
    public record ChatAnswer(
            @NotBlank @Size(max = 80) String questionId,
            @Size(max = 16) List<@NotBlank @Size(max = 200) String> values,
            @Size(max = 2000) String freeText,
            Boolean skipped) {}

    /**
     * 本期只接受类型明确的合成上下文：不接受任意 Map 作为「已确认事实」。
     * 真实业务上下文（指标、口径）属于后续受控查询设计。
     */
    public record ChatContext(@Size(max = 80) String topic, @Size(max = 80) String period) {}
}
