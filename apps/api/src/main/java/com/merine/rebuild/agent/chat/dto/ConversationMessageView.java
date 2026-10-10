package com.merine.rebuild.agent.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 会话里的一条消息；助手消息沿用流协议的 messageId，失败原因在对应的执行记录里。 */
@Schema(name = "ChatConversationMessage")
public record ConversationMessageView(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ChatRunView.ChatRunState status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String text,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {}
