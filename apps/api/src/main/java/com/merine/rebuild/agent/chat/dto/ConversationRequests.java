package com.merine.rebuild.agent.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ConversationRequests {
    private ConversationRequests() {}

    /** 重命名会话：版本用于乐观锁，避免两个标签页互相覆盖。 */
    @Schema(name = "RenameChatConversation")
    public record Rename(
            @NotBlank @Size(max = 80) String title,
            @Min(0) int version) {}
}
