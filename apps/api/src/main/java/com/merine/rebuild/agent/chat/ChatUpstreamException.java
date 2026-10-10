package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatErrorCode;

/** 上游调用失败：错误码与可重试性直接映射为 SSE ERROR 事件，不把上游原文带回前端。 */
final class ChatUpstreamException extends RuntimeException {

    private final ChatErrorCode code;
    private final boolean retryable;

    ChatUpstreamException(ChatErrorCode code, String message, boolean retryable) {
        super(message, null, false, false);
        this.code = code;
        this.retryable = retryable;
    }

    ChatErrorCode code() {
        return code;
    }

    boolean retryable() {
        return retryable;
    }
}
